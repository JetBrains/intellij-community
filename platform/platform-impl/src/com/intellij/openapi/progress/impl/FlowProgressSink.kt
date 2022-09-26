// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressSink
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class FlowProgressSink : ProgressSink {

  private val _stateFlow = MutableStateFlow(ProgressState(null, null, -1.0))

  val stateFlow: Flow<ProgressState> = _stateFlow.asStateFlow()

  val state: ProgressState
    get() = _stateFlow.value

  override fun update(text: @ProgressText String?, details: @ProgressDetails String?, fraction: Double?) {
    check(fraction == null || fraction in 0.0..1.0)
    _stateFlow.update {
      ProgressState(
        text = text ?: it.text,
        details = details ?: it.details,
        fraction = fraction ?: it.fraction
      )
    }
  }
}
