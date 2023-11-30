// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class IndeterminateTextDetailsProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<TextDetails> = ChildrenHandler(cs, TextDetails.NULL, ::reduceTextDetails)

  val progressUpdates: Flow<TextDetails>
    get() = childrenHandler.progressUpdates.map {
      it.state
    }

  override fun createStep(duration: Double, text: ProgressText?): ProgressReporter {
    if (text == null) {
      val reporter = IndeterminateTextDetailsProgressReporter(cs)
      childrenHandler.applyChildUpdates(reporter, reporter.progressUpdates)
      return reporter
    }
    else {
      val reporter = IndeterminateTextProgressReporter(cs)
      val childUpdates = reporter.progressUpdates.map { childText ->
        TextDetails(text = text, details = childText)
      }
      childrenHandler.applyChildUpdates(reporter, childUpdates)
      return reporter
    }
  }

  override fun asRawReporter(): RawProgressReporter = object : RawProgressReporter {

    private fun rawUpdate(updater: (TextDetails) -> TextDetails) {
      childrenHandler.progressState.update { (_, state) ->
        FractionState(-1.0, updater(state))
      }
    }

    override fun text(text: ProgressText?) {
      rawUpdate {
        it.copy(text = text)
      }
    }

    override fun details(details: @ProgressDetails String?) {
      rawUpdate {
        it.copy(details = details)
      }
    }
  }
}
