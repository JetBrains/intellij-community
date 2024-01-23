// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.progress.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal class ProgressStepImpl(
  private val parentScope: CoroutineScope,
  private val config: StepConfig,
  private val state: MutableStateFlow<StepState> = MutableStateFlow(initialState),
) : ProgressStep {

  override fun toString(): String {
    return if (_taken.get()) {
      "ProgressStep(config = $config, state = ${state.value})"
    }
    else {
      "ProgressStep(fresh)"
    }
  }

  private val _taken = AtomicBoolean()

  override fun progressUpdates(): Flow<StepState> {
    return state
  }

  override suspend fun <X> withText(text: ProgressText, action: suspend CoroutineScope.() -> X): X {
    if (!takeOwnership()) {
      return ignoreProgressReportingIn(action)
    }
    val childConfig = config.childConfig(indeterminate = false, hasText = true)
    val childStep: ProgressStep = if (childConfig == null) {
      EmptyProgressStep
    }
    else {
      ProgressStepImpl(parentScope, childConfig)
    }
    val updates = if (config.textLevel > 0) {
      childStep.progressUpdates().pushTextToDetails(text)
    }
    else {
      childStep.progressUpdates()
    }
    val subscription = parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        state.emitAll(updates)
      }
      finally {
        state.value = doneState
      }
    }
    try {
      return withContext(childStep.asContextElement(), action)
    }
    finally {
      subscription.cancel()
    }
  }

  override fun asSequential(size: Int): SequentialProgressReporterHandle? {
    if (!validateSizeAndTakeOwnership(size)) {
      return null
    }
    return SequentialProgressReporterImpl(ScalingStep(parentScope, size, state, config))
  }

  override fun asConcurrent(size: Int): ProgressReporterHandle? {
    if (!validateSizeAndTakeOwnership(size)) {
      return null
    }
    return ProgressReporterImpl(ScalingStep(parentScope, size, state, config))
  }

  override fun asRaw(): RawProgressReporterHandle? {
    if (!takeOwnership()) {
      return null
    }
    val reporter = RawProgressReporterImpl(config)
    return RawProgressReporterHandleImpl(parentScope, state, reporter)
  }

  private fun validateSizeAndTakeOwnership(size: Int): Boolean {
    if (size < 0) {
      LOG.error(IllegalArgumentException("Trying to split the progress step into ${size} parts"))
      return false
    }
    return takeOwnership()
  }

  private fun takeOwnership(): Boolean {
    if (!_taken.compareAndSet(false, true)) {
      // TODO log error if logging turned on
      return false
    }
    return true
  }
}
