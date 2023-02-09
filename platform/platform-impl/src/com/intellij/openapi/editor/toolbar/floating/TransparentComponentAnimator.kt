// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.TimerUtil
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@Suppress("SameParameterValue")
@ApiStatus.Internal
class TransparentComponentAnimator(
  private val component: TransparentComponent,
  parentDisposable: Disposable
) {

  private val disposable = Disposer.newCheckedDisposable(parentDisposable)
  private val executor = ExecutorWithThrottling(THROTTLING_DELAY)
  private val clk = TimerUtil.createNamedTimer("CLK", CLK_DELAY)
  private val state = AtomicReference<State>(State.Invisible)

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

  fun scheduleShow() = executor.executeOrSkip {
    updateState(::nextShowingState)
  }

  fun scheduleHide() {
    updateState(::nextHidingState)
  }

  fun hideImmediately() {
    updateState { State.Invisible }
  }

  private fun updateState() {
    updateState(::nextState)
  }

  private fun updateState(nextState: (State) -> State) {
    if (!disposable.isDisposed) {
      lateinit var oldState: State
      val state = state.updateAndGet {
        oldState = it
        nextState(it)
      }
      updateTimer(state)
      invokeLaterIfNeeded {
        updateComponent(oldState, state)
      }
    }
  }

  private fun updateTimer(state: State) {
    val oldDelay = clk.delay
    val delay = when (state) {
      is State.Invisible -> Int.MAX_VALUE
      is State.Visible -> RETENTION_CLK_DELAY
      is State.Hiding -> CLK_DELAY
      is State.Showing -> CLK_DELAY
    }
    clk.delay = delay
    clk.initialDelay = delay
    if (oldDelay != delay) {
      stopTimerIfNeeded()
    }
    when (state) {
      is State.Invisible -> stopTimerIfNeeded()
      is State.Visible -> when (component.autoHideable) {
        true -> startTimerIfNeeded()
        else -> stopTimerIfNeeded()
      }
      is State.Hiding -> startTimerIfNeeded()
      is State.Showing -> startTimerIfNeeded()
    }
  }

  private fun updateComponent(oldState: State, state: State) {
    component.setOpacity(getOpacity(state))
    val wasVisible = oldState.isVisible()
    val isVisible = state.isVisible()
    when {
      !wasVisible && isVisible -> component.showComponent()
      wasVisible && !isVisible -> component.hideComponent()
    }
    component.repaintComponent()
  }

  private fun nextShowingState(state: State): State {
    return when (state) {
      is State.Invisible -> State.Showing(0)
      is State.Visible -> State.Visible(0)
      is State.Hiding -> State.Showing(SHOWING_COUNT - SHOWING_COUNT * state.count / HIDING_COUNT)
      is State.Showing -> state
    }
  }

  private fun nextHidingState(state: State): State {
    return when (state) {
      is State.Invisible -> State.Invisible
      is State.Visible -> State.Hiding(0)
      is State.Hiding -> state
      is State.Showing -> State.Hiding(HIDING_COUNT - HIDING_COUNT * state.count / SHOWING_COUNT)
    }
  }

  private fun nextState(state: State): State {
    return when (state) {
      is State.Invisible -> State.Invisible
      is State.Visible -> when {
        !component.autoHideable -> State.Visible(0)
        component.isComponentOnHold() -> State.Visible(0)
        state.count + 1 >= AUTO_RETENTION_COUNT -> State.Hiding(0)
        else -> State.Visible(state.count + 1)
      }
      is State.Hiding -> when {
        state.count + 1 >= HIDING_COUNT -> State.Invisible
        else -> State.Hiding(state.count + 1)
      }
      is State.Showing -> when {
        state.count + 1 >= SHOWING_COUNT -> State.Visible(0)
        else -> State.Showing(state.count + 1)
      }
    }
  }

  private fun getOpacity(state: State): Float {
    return when (state) {
      is State.Invisible -> 0.0f
      is State.Visible -> 1.0f
      is State.Hiding -> 1.0f - getOpacity(state.count, HIDING_COUNT)
      is State.Showing -> getOpacity(state.count, SHOWING_COUNT)
    }
  }

  private fun getOpacity(count: Int, maxCount: Int): Float {
    return maxOf(0.0f, minOf(1.0f, count / maxCount.toFloat()))
  }

  init {
    clk.isRepeats = true
    clk.addActionListener { updateState() }
    disposable.whenDisposed { stopTimerIfNeeded() }
  }

  private sealed interface State {
    data class Visible(val count: Int) : State
    data class Hiding(val count: Int) : State
    data class Showing(val count: Int) : State
    object Invisible : State {
      override fun toString() = "Invisible"
    }

    fun isVisible(): Boolean {
      return this !is Invisible
    }
  }

  companion object {
    private const val CLK_DELAY = 1000 / 60
    private const val SHOWING_COUNT = 500 / CLK_DELAY
    private const val HIDING_COUNT = 1000 / CLK_DELAY

    private const val RETENTION_CLK_DELAY = 1500
    private const val AUTO_RETENTION_COUNT = 1500 / RETENTION_CLK_DELAY

    private const val THROTTLING_DELAY = 1000
  }
}