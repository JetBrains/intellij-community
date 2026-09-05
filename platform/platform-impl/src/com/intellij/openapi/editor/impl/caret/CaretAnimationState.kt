// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.impl.caret.blink.CaretBlinkMachine
import com.intellij.openapi.editor.impl.caret.model.CaretCursorSnapshot
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRepaintMetrics
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.caret.motion.CaretMotionMachine
import com.intellij.openapi.editor.impl.view.animation.AnimationClock
import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import kotlin.time.TimeSource

internal class CaretAnimationState private constructor(
  private val motion: CaretMotionMachine,
  private val blink: CaretBlinkMachine,
  private val repaintMetrics: CaretRepaintMetrics,
  val snapshot: CaretCursorSnapshot,
  val lastFrameAt: AnimationTimeMark,
  val isRunning: Boolean,
  val version: Long,
) {
  val isMotionSettled: Boolean get() = motion.isSettled

  /// MARK: motion transitions

  fun retarget(
    placements: List<CaretPlacement>,
    tick: CaretTick,
    isCaretShown: Boolean,
    repaintMetrics: CaretRepaintMetrics,
  ): CaretAnimationState = next(motion = motion.retarget(placements, tick, isCaretShown), repaintMetrics = repaintMetrics)

  fun snapTo(placements: List<CaretPlacement>, tick: CaretTick, repaintMetrics: CaretRepaintMetrics): CaretAnimationState =
    next(motion = motion.snapTo(placements, tick), repaintMetrics = repaintMetrics)

  fun settle(tick: CaretTick): CaretAnimationState = next(motion = motion.settle(tick))

  /// MARK: blink transitions

  fun startBlink(): CaretAnimationState = next(blink = blink.start())

  fun stopBlink(): CaretAnimationState = next(blink = blink.stop())

  fun restartBlink(): CaretAnimationState = next(blink = blink.restart())

  /// MARK: snapshot updates

  fun withEnabled(enabled: Boolean): CaretAnimationState {
    val nextSnapshot = snapshot.withEnabled(enabled)
    return if (nextSnapshot === snapshot) this else next(snapshot = nextSnapshot)
  }

  fun withShown(shown: Boolean, now: AnimationTimeMark): CaretAnimationState = next(
    blink = if (shown) blink.start() else blink.stop(),
    snapshot = snapshot.withShown(shown, now),
  )

  fun setFullOpacity(): CaretAnimationState = next(snapshot = snapshot.makeFullyOpaque())

  fun withStartTime(startTime: AnimationTimeMark): CaretAnimationState = next(snapshot = snapshot.withStartTime(startTime))

  /// MARK: frame updates

  fun withRunning(running: Boolean): CaretAnimationState =
    if (running == isRunning) this else next(isRunning = running)

  fun freeze(now: AnimationTimeMark): Pair<CaretAnimationState, CaretStep> = next(lastFrameAt = now) to CaretStep.IDLE

  fun advance(tick: CaretTick, prefetching: Boolean): Pair<CaretAnimationState, CaretStep> {
    val (nextMotion, motionStep) = motion.advance(tick, prefetching)
    val (nextBlink, blinkStep) = blink.advance(tick, prefetching)

    val locations = if (motionStep.moved) nextMotion.locations else null
    val nextSnapshot = snapshot.withFrame(locations, blinkStep.opacity, tick.now, repaintMetrics)
    val nextState = next(motion = nextMotion, blink = nextBlink, snapshot = nextSnapshot, lastFrameAt = tick.now)
    val prefetch = run {
      if (motionStep.prefetch != null) return@run motionStep.prefetch

      nextSnapshot.locations.asList().takeIf { blinkStep.wantsPrefetch && it.isNotEmpty() }
    }
    val step = CaretStep(
      moved = motionStep.moved,
      opacityChanged = nextSnapshot.blinkOpacity.level != snapshot.blinkOpacity.level,
      prefetch = prefetch,
      nextDelay = minOf(motionStep.nextDelay, blinkStep.nextDelay),
      nextState.version
    )
    return nextState to step
  }

  /// MARK: creation

  private fun next(
    motion: CaretMotionMachine = this.motion,
    blink: CaretBlinkMachine = this.blink,
    repaintMetrics: CaretRepaintMetrics = this.repaintMetrics,
    snapshot: CaretCursorSnapshot = this.snapshot,
    lastFrameAt: AnimationTimeMark = this.lastFrameAt,
    isRunning: Boolean = this.isRunning,
  ): CaretAnimationState = CaretAnimationState(motion, blink, repaintMetrics, snapshot, lastFrameAt, isRunning, version + 1)

  companion object {
    fun initial(): CaretAnimationState = CaretAnimationState(
      motion = CaretMotionMachine.DORMANT,
      blink = CaretBlinkMachine.DORMANT,
      repaintMetrics = CaretRepaintMetrics.EMPTY,
      snapshot = CaretCursorSnapshot.INITIAL,
      lastFrameAt = AnimationClock.markAnimationNow(),
      isRunning = false,
      version = 0L,
    )
  }
}

private const val OPACITY_LEVELS = 255f

private val Float.level: Int get() = (this * OPACITY_LEVELS).toInt()
