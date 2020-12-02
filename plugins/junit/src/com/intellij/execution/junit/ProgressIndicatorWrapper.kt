// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit

import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator

/**
 * This a temporary solution to be used to adapt [TargetProgressIndicator] as [ProgressIndicator] to pass it to
 * [com.intellij.jarRepository.JarRepositoryManager.loadDependenciesSync].
 *
 * @see TestObject.downloadDependenciesWhenRequired
 * @see com.intellij.jarRepository.JarRepositoryManager.loadDependenciesSync
 */
internal class ProgressIndicatorWrapper(private val targetProgressIndicator: TargetProgressIndicator) : ProgressIndicator {
  private var indeterminate: Boolean = true

  private var fraction: Double = 0.0

  override fun start() = Unit

  override fun stop() = targetProgressIndicator.stop()

  override fun isRunning(): Boolean = !targetProgressIndicator.isStopped

  override fun cancel() = Unit

  override fun isCanceled(): Boolean = targetProgressIndicator.isCanceled

  override fun setText(text: String) {
    targetProgressIndicator.addSystemLine("$SYSTEM_LINE_PREFIX_FOR_TEXT$text")
  }

  override fun getText(): String? = null

  override fun setText2(text: String) {
    targetProgressIndicator.addSystemLine("$SYSTEM_LINE_PREFIX_FOR_TEXT_2$text")
  }

  override fun getText2(): String? = null

  override fun getFraction(): Double = fraction

  override fun setFraction(fraction: Double) {
    this.fraction = fraction
  }

  override fun pushState() = Unit

  override fun popState() = Unit

  override fun isModal(): Boolean = false

  override fun getModalityState(): ModalityState = ModalityState.any()

  override fun setModalityProgress(modalityProgress: ProgressIndicator?) = Unit

  override fun isIndeterminate(): Boolean = indeterminate

  override fun setIndeterminate(indeterminate: Boolean) {
    this.indeterminate = indeterminate
  }

  override fun checkCanceled() {
    if (isCanceled) throw ProcessCanceledException()
  }

  override fun isPopupWasShown(): Boolean = false

  override fun isShowing(): Boolean = false

  companion object {
    private const val RIGHTWARDS_WHITE_ARROW = '\u21e8'

    private const val INDENT = "  "

    private const val SYSTEM_LINE_PREFIX_FOR_TEXT = "$INDENT$RIGHTWARDS_WHITE_ARROW "

    private const val SYSTEM_LINE_PREFIX_FOR_TEXT_2 = "$INDENT$INDENT$RIGHTWARDS_WHITE_ARROW "
  }
}