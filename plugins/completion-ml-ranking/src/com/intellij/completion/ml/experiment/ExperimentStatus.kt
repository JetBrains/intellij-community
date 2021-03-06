// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.experiment

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