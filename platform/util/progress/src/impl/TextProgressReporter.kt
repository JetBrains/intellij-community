// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class TextProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<ProgressText?> = ChildrenHandler(cs, null, ::reduceText)

  val progressUpdates: Flow<FractionState<ProgressText?>> = childrenHandler.progressUpdates

  override fun createStep(duration: Double, text: ProgressText?): ProgressReporter {
    when {
      text == null && duration == 0.0 -> {
        val step = IndeterminateTextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, step.progressUpdates)
        return step
      }
      text == null && duration != 0.0 -> {
        val step = TextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.progressUpdates)
        return step
      }
      text != null && duration == 0.0 -> {
        val step = SilentProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, flowOf(text))
        return step
      }
      else /* text != null && duration != 0.0 */ -> {
        val step = FractionReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.progressUpdates.map { childFraction ->
          FractionState(fraction = childFraction, text)
        })
        return step
      }
    }
  }

  override fun asRawReporter(): RawProgressReporter = object : RawProgressReporter {

    override fun text(text: ProgressText?) {
      childrenHandler.progressState.update { fractionState ->
        fractionState.copy(state = text)
      }
    }

    override fun fraction(fraction: Double?) {
      if (!checkFraction(fraction)) {
        return
      }
      childrenHandler.progressState.update { fractionState ->
        fractionState.copy(fraction = fraction ?: -1.0)
      }
    }
  }
}
