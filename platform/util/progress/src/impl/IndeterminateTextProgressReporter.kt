// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class IndeterminateTextProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<ProgressText?> = ChildrenHandler(cs, null, ::reduceText)

  val progressUpdates: Flow<ProgressText?>
    get() = childrenHandler.progressUpdates.map {
      it.state
    }

  override fun createStep(duration: Double, text: ProgressText?): ProgressReporter {
    if (text == null) {
      val reporter = IndeterminateTextProgressReporter(cs)
      childrenHandler.applyChildUpdates(reporter, reporter.progressUpdates)
      return reporter
    }
    else {
      val reporter = SilentProgressReporter(cs)
      childrenHandler.applyChildUpdates(reporter, flowOf(text))
      return reporter
    }
  }

  override fun asRawReporter(): RawProgressReporter = object : RawProgressReporter {

    override fun text(text: ProgressText?) {
      childrenHandler.progressState.value = FractionState(-1.0, text)
    }
  }
}
