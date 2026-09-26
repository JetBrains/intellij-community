// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.platform.util.progress.impl.LOG
import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.StepConfig
import com.intellij.platform.util.progress.impl.initialState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal class RawProgressReporterImpl(private val config: StepConfig) : RawProgressReporter {

  private val _state: MutableStateFlow<StepState> = MutableStateFlow(initialState)

  val state: StateFlow<StepState> = _state

  override fun text(text: ProgressText?) {
    if (config.textLevel > 0) {
      _state.update {
        it.copy(text = text)
      }
    }
  }

  override fun details(details: @ProgressDetails String?) {
    if (config.textLevel > 1) {
      _state.update {
        it.copy(details = details)
      }
    }
  }

  override fun fraction(fraction: Double?) {
    if (fraction != null && fraction !in 0.0..1.0) {
      LOG.error(IllegalArgumentException("Fraction is expected to be `null` or a value in [0.0; 1.0], got $fraction"))
      return
    }
    if (!config.isIndeterminate) {
      _state.update {
        it.copy(fraction = fraction)
      }
    }
  }
}
