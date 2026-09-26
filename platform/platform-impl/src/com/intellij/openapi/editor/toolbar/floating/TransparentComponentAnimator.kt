// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.util.animation.ShowHideAnimator
import com.intellij.util.ui.TimerUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TransparentComponentAnimator(
  private val component: TransparentComponent,
  parentDisposable: Disposable,
) {

  private val disposable = Disposer.newCheckedDisposable(parentDisposable)

  private val clk = TimerUtil.createNamedTimer("CLK", RETENTION_TIME_MS)

  private val animator = ShowHideAnimator { progress ->
    component.setOpacity(progress.toFloat())
    component.repaintComponent()
  }

  var showingTime: Int by animator::showingDuration

  var hidingTime: Int by animator::hidingDuration

  var retentionTime: Int by clk::initialDelay

  var autoHideable: Boolean = false

  private fun startTimerIfNeeded() {
    if (!disposable.isDisposed) {
      if (!clk.isRunning) {
        clk.start()
      }
    }
  }

  private fun stopTimerIfNeeded() {
    if (clk.isRunning) {
      clk.stop()
    }
  }

  fun scheduleShow() {
    stopTimerIfNeeded()
    val onCompletion = if (autoHideable) ::startTimerIfNeeded else null
    animator.setVisible(true, onCompletion) {
      component.showComponent()
    }
  }

  fun scheduleHide() {
    stopTimerIfNeeded()
    animator.setVisible(false) {
      component.hideComponent()
    }
  }

  fun hideImmediately() {
    stopTimerIfNeeded()
    component.hideComponent()
    animator.setVisibleImmediately(false)
  }

  init {
    Disposer.register(parentDisposable, animator.disposable)
    animator.hidingDelay = 0
    animator.showingDelay = 0
    animator.hidingDuration = HIDING_TIME_MS
    animator.showingDuration = SHOWING_TIME_MS
  }

  init {
    clk.isRepeats = false
    disposable.whenDisposed {
      stopTimerIfNeeded()
    }
    clk.addActionListener {
      if (!component.isComponentOnHold()) {
        scheduleHide()
      }
    }
  }

  init {
    component.hideComponent()
  }

  companion object {
    const val SHOWING_TIME_MS = 500
    const val HIDING_TIME_MS = 1000
    const val RETENTION_TIME_MS = 1500
  }
}
