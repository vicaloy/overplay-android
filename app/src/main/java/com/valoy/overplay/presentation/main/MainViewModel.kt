package com.valoy.overplay.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valoy.overplay.di.IoDispatcher
import com.valoy.overplay.di.SessionTimeout
import com.valoy.overplay.domain.models.Gyroscope
import com.valoy.overplay.domain.repository.RotationRepository
import com.valoy.overplay.domain.repository.SessionRepository
import com.valoy.overplay.util.tryCatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val rotationRepository: RotationRepository,
    private val sessionRepository: SessionRepository,
    @SessionTimeout private val sessionTimeout: Int,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(MainUiState(letterSize = DEFAULT_SIZE, sessionCount = ZERO))
    val uiState = _uiState

    fun onResume() {
        startNewSession()
        startListeningGyroscope()
    }

    fun onStop() {
        saveSessionTime()
        onStopListeningGyroscope()
    }

    private fun startListeningGyroscope() {
        viewModelScope.launch(dispatcher) {
            tryCatch {
                rotationRepository.get().collect { degrees ->
                    uiState.update { state ->
                        state.copy(letterSize = calculateLetterSize((degrees as Gyroscope).z))
                    }
                }
            }
        }
    }

    private fun onStopListeningGyroscope() {
        rotationRepository.flush()
    }

    private fun saveSessionTime() {
        viewModelScope.launch(dispatcher) {
            tryCatch {
                sessionRepository.saveTime(System.currentTimeMillis())
            }
        }
    }

    private fun startNewSession() {
        viewModelScope.launch(dispatcher) {
            tryCatch {
                val time = sessionRepository.getTime()
                val count = sessionRepository.getCount() + ONE
                val elapsedTime = System.currentTimeMillis() - time
                if (shouldStartNewSession(elapsedTime)) {
                    uiState.update { state ->
                        state.copy(sessionCount = count)
                    }
                    sessionRepository.saveCount(count)
                }
            }
        }
    }

    private fun shouldStartNewSession(elapsedTime: Long): Boolean = elapsedTime > sessionTimeout

    private fun calculateLetterSize(degrees: Double): Int = when {
        degrees >= THIRTY_DEGREES_LEFT -> TWELVE_SIZE
        degrees >= THIRTY_DEGREES_RIGHT -> TWENTY_SIZE
        else -> DEFAULT_SIZE
    }

    private companion object {
        const val ZERO = 0
        const val ONE = 1
        const val DEFAULT_SIZE = 16
        const val TWELVE_SIZE = 12
        const val TWENTY_SIZE = 20
        const val THIRTY_DEGREES_LEFT = 30
        const val THIRTY_DEGREES_RIGHT = -30
    }
}
