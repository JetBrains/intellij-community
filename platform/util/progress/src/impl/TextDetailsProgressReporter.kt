// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class TextDetailsProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<TextDetails> = ChildrenHandler(cs, TextDetails.NULL, ::reduceTextDetails)

  val progressState: Flow<ProgressState> = childrenHandler.progressState.map { (fraction, state) ->
    ProgressState(text = state.text, details = state.details, fraction = fraction)
  }

  override fun createStep(duration: Double, text: ProgressText?): ProgressReporter {
    when {
      text == null && duration == 0.0 -> {
        val step = IndeterminateTextDetailsProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, step.progressUpdates)
        return step
      }
      text == null && duration != 0.0 -> {
        val step = TextDetailsProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.childrenHandler.progressUpdates)
        return step
      }
      text != null && duration == 0.0 -> {
        val step = IndeterminateTextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, step.progressUpdates.map { childText ->
          TextDetails(text, details = childText)
        })
        return step
      }
      else /* text != null && duration != 0.0 */ -> {
        val step = TextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.progressUpdates.map { (childFraction, childText) ->
          FractionState(childFraction, TextDetails(text, details = childText))
        })
        return step
      }
    }
  }

  override fun asRawReporter(): RawProgressReporter = object : RawProgressReporter {

    override fun text(text: ProgressText?) {
      childrenHandler.progressState.update { fractionState ->
        fractionState.copy(state = fractionState.state.copy(text = text))
      }
    }

    override fun details(details: @ProgressDetails String?) {
      childrenHandler.progressState.update { fractionState ->
        fractionState.copy(state = fractionState.state.copy(details = details))
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
