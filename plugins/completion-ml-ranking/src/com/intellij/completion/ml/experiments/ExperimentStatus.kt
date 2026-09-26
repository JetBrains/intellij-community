package com.intellij.completion.ml.experiments

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ExperimentStatus {
  companion object {
    fun getInstance(): ExperimentStatus = service()
  }

  fun forLanguage(language: Language): ExperimentInfo
  fun disable()
  fun isDisabled(): Boolean
}