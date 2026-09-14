// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.impl.caret.blink.CaretBlinkMachine
import com.intellij.openapi.editor.impl.caret.blink.CaretBlinkStep
import com.intellij.openapi.editor.impl.caret.model.CaretCursorSnapshot
import com.intellij.openapi.editor.impl.caret.model.CaretFrameInterval
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.caret.model.CaretRepaintMetrics
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.caret.motion.CaretMotionMachine
import com.intellij.openapi.editor.impl.caret.motion.CaretMotionStep
import com.intellij.openapi.editor.impl.view.animation.AnimationClock
import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import kotlin.time.Duration

/**
 * The whole animation state of one editor: where the carets are heading, how they blink, and the snapshot the painter
 * reads. Every transition returns a new instance with a higher [version], so a concurrent writer is always detectable.
 */
internal class CaretAnimationState private constructor(
  private val motion: CaretMotionMachine,
  private val blink: CaretBlinkMachine,
  private val repaintMetrics: CaretRepaintMetrics,
  val snapshot: CaretCursorSnapshot,
  private val lastFrameAt: AnimationTimeMark,
  val isRunning: Boolean,
  val version: Long,
) {
  val isMotionSettled: Boolean get() = motion.isSettled

  /**
   * How long the previous frame actually took, floored at one frame interval so that a long pause does not make the
   * next frame jump.
   */
  fun frameDurationAt(now: AnimationTimeMark): Duration {
    val sinceLastFrame = now - lastFrameAt
    return sinceLastFrame.coerceAtLeast(CaretFrameInterval.MOVEMENT)
  }

  /// MARK: motion transitions

  fun retarget(
    placements: List<CaretPlacement>,
    tick: CaretTick,
    isCaretShown: Boolean,
    repaintMetrics: CaretRepaintMetrics,
  ): CaretAnimationState {
    val nextMotion = motion.retarget(placements, tick, isCaretShown)
    return next(motion = nextMotion, repaintMetrics = repaintMetrics)
  }

  fun snapTo(
    placements: List<CaretPlacement>,
    tick: CaretTick,
    repaintMetrics: CaretRepaintMetrics,
  ): CaretAnimationState {
    val nextMotion = motion.snapTo(placements, tick)
    return next(motion = nextMotion, repaintMetrics = repaintMetrics)
  }

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

  fun withShown(shown: Boolean, now: AnimationTimeMark): CaretAnimationState {
    val nextBlink = if (shown) blink.start() else blink.stop()
    val nextSnapshot = snapshot.withShown(shown, now)
    return next(blink = nextBlink, snapshot = nextSnapshot)
  }

  fun showFullyOpaque(): CaretAnimationState = next(snapshot = snapshot.shownFullyOpaque())

  fun withActivityAt(activityAt: AnimationTimeMark): CaretAnimationState =
    next(snapshot = snapshot.withActivityAt(activityAt))

  /// MARK: frame updates

  fun withRunning(running: Boolean): CaretAnimationState {
    return if (running == isRunning) {
      this
    } else {
      next(isRunning = running)
    }
  }

  /**
   * Holds the animation where it is, for example while a bulk document update runs.
   */
  fun freeze(now: AnimationTimeMark): Pair<CaretAnimationState, CaretStep> {
    val frozen = next(lastFrameAt = now)
    return frozen to CaretStep.IDLE
  }

  fun advance(tick: CaretTick, prefetching: Boolean): Pair<CaretAnimationState, CaretStep> {
    val (nextMotion, motionStep) = motion.advance(tick, prefetching)
    val (nextBlink, blinkStep) = blink.advance(tick, prefetching)
    val movedLocations = if (motionStep.moved) nextMotion.locations else null
    val nextSnapshot = snapshot.withStep(movedLocations, blinkStep.opacity, tick.now, repaintMetrics)
    val nextState = next(
      motion = nextMotion,
      blink = nextBlink,
      snapshot = nextSnapshot,
      lastFrameAt = tick.now,
    )
    val step = stepFor(motionStep, blinkStep, nextSnapshot, nextState.version)
    return nextState to step
  }

  private fun stepFor(
    motionStep: CaretMotionStep,
    blinkStep: CaretBlinkStep,
    nextSnapshot: CaretCursorSnapshot,
    version: Long,
  ): CaretStep {
    val opacityChanged = nextSnapshot.opacityDiffersFrom(snapshot)
    return CaretStep(
      moved = motionStep.moved,
      opacityChanged = opacityChanged,
      prefetch = prefetchFor(motionStep, blinkStep, nextSnapshot),
      nextDelay = minOf(motionStep.nextDelay, blinkStep.nextDelay),
      version = version,
    )
  }

  /**
   * A move prefetches every frame it is going to paint; a blink can only prefetch where the caret already is.
   */
  private fun prefetchFor(
    motionStep: CaretMotionStep,
    blinkStep: CaretBlinkStep,
    nextSnapshot: CaretCursorSnapshot,
  ): List<CaretRectangle>? {
    val motionPrefetch = motionStep.prefetch
    if (motionPrefetch != null) {
      return motionPrefetch
    }
    val canPrefetchBlink = blinkStep.wantsPrefetch && nextSnapshot.locations.isNotEmpty()
    return if (canPrefetchBlink) {
      nextSnapshot.locations.asList()
    } else {
      null
    }
  }

  /// MARK: creation

  private fun next(
    motion: CaretMotionMachine = this.motion,
    blink: CaretBlinkMachine = this.blink,
    repaintMetrics: CaretRepaintMetrics = this.repaintMetrics,
    snapshot: CaretCursorSnapshot = this.snapshot,
    lastFrameAt: AnimationTimeMark = this.lastFrameAt,
    isRunning: Boolean = this.isRunning,
  ): CaretAnimationState {
    return CaretAnimationState(motion, blink, repaintMetrics, snapshot, lastFrameAt, isRunning, version + 1)
  }

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
