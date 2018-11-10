// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.vcs.log.ui.frame.ProgressStripe

class ProgressStripeProgressIndicator(private val progressStripe: ProgressStripe, private val checkCanceledOnStart: Boolean = false)
  : AbstractProgressIndicatorExBase(true) {

  private val modalityState = ModalityState.stateForComponent(progressStripe)

  override fun start() {
    if (checkCanceledOnStart) checkCanceled()
    super.start()
  }

  override fun getModalityState() = modalityState

  override fun onRunningChange() {
    runInEdt(modalityState) {
      if (isRunning) progressStripe.startLoading() else progressStripe.stopLoading()
    }
  }
}