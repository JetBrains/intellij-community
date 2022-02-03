// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FlowProgressSink : ProgressSink {

  private val _stateFlow = MutableStateFlow(ProgressState(null, null, -1.0))

  val stateFlow: Flow<ProgressState> = _stateFlow

  var state: ProgressState
    get() = _stateFlow.value
    private set(value) {
      _stateFlow.value = value
    }

  override fun text(text: String) {
    state = state.copy(text = text)
  }

  override fun details(details: String) {
    state = state.copy(details = details)
  }

  override fun fraction(fraction: Double) {
    check(fraction in 0.0..1.0)
    state = state.copy(fraction = fraction)
  }
}
