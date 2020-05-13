// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.util.ui.TimerUtil


abstract class VisibilityController : Disposable {

  protected abstract val autoHide: Boolean
  protected abstract fun setVisible(isVisible: Boolean)
  protected abstract fun repaint()
  protected abstract fun isRetention(): Boolean

  private var isDisposed = false
  private var opacityCounter = 0
  private var state = State.INVISIBLE

  private val transitionTimer = TimerUtil.createNamedTimer("Transition timer", TRANSITION_DELAY)
  private val retentionTimer = TimerUtil.createNamedTimer("Retention timer", RETENTION_DELAY)

  val opacity get() = synchronized(this) { opacityCounter / 100.0f }

  fun scheduleHide() = synchronized(this) {
    state = when (state) {
      State.INVISIBLE -> State.INVISIBLE
      State.SHOWING, State.HIDING, State.VISIBLE -> State.HIDING
    }
    propagateStateChanges()
  }

  fun scheduleShow() = synchronized(this) {
    state = when (state) {
      State.VISIBLE -> State.VISIBLE
      State.SHOWING, State.HIDING, State.INVISIBLE -> State.SHOWING
    }
    propagateStateChanges()
  }

  private fun refresh() = synchronized(this) {
    @Suppress("NON_EXHAUSTIVE_WHEN")
    when (state) {
      State.HIDING -> opacityCounter -= TRANSITION_VALUE
      State.SHOWING -> opacityCounter += TRANSITION_VALUE
    }
    opacityCounter = minOf(opacityCounter, MAX_VALUE)
    opacityCounter = maxOf(opacityCounter, MIN_VALUE)
    when (opacityCounter) {
      MAX_VALUE -> state = State.VISIBLE
      MIN_VALUE -> state = State.INVISIBLE
    }
    propagateStateChanges()
  }

  private fun propagateStateChanges() {
    if (isDisposed) return
    when (state) {
      State.VISIBLE, State.INVISIBLE -> transitionTimer.stop()
      State.SHOWING, State.HIDING -> transitionTimer.start()
    }
    when (state) {
      State.VISIBLE -> if (autoHide) retentionTimer.restart()
      State.INVISIBLE, State.SHOWING, State.HIDING -> retentionTimer.stop()
    }
    when (state) {
      State.INVISIBLE -> setVisible(false)
      State.VISIBLE, State.SHOWING, State.HIDING -> setVisible(true)
    }
    repaint()
  }

  override fun dispose() {
    synchronized(this) {
      isDisposed = true
      transitionTimer.stop()
      retentionTimer.stop()
    }
  }

  init {
    retentionTimer.isRepeats = false
    retentionTimer.addActionListener { if (isRetention()) scheduleShow() else scheduleHide() }
    transitionTimer.isRepeats = true
    transitionTimer.addActionListener { refresh() }
  }

  private enum class State { INVISIBLE, VISIBLE, HIDING, SHOWING }

  companion object {
    private const val RETENTION_DELAY = 1500
    private const val TRANSITION_DELAY = 50
    private const val TRANSITION_VALUE = 20
    private const val MAX_VALUE = 100
    private const val MIN_VALUE = 0
  }
}