// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class FractionReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<Nothing?> = ChildrenHandler(cs, null) { null }

  val progressUpdates: Flow<Double>
    get() = childrenHandler.progressUpdates.map {
      it.fraction
    }

  override fun createStep(duration: Double, text: ProgressText?): ProgressReporter {
    if (duration == 0.0) {
      return SilentProgressReporter(cs)
    }
    else {
      val step = FractionReporter(cs)
      childrenHandler.applyChildUpdates(step, duration, step.childrenHandler.progressUpdates)
      return step
    }
  }

  override fun asRawReporter(): RawProgressReporter = object : RawProgressReporter {

    override fun fraction(fraction: Double?) {
      if (!checkFraction(fraction)) {
        return
      }
      childrenHandler.progressState.value = FractionState(fraction ?: -1.0, null)
    }
  }
}
