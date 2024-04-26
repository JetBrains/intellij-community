// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.doneState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch

internal class RawProgressReporterHandleImpl(
  parentScope: CoroutineScope,
  private val state: MutableStateFlow<StepState>,
  override val reporter: RawProgressReporterImpl,
) : RawProgressReporterHandle {

  private val subscription = parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
    try {
      state.emitAll(reporter.state)
    }
    finally {
      state.value = doneState
    }
  }

  override fun close() {
    subscription.cancel()
  }
}
