// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.impl.caret.blink.CaretBlinkMachine
import com.intellij.openapi.editor.impl.caret.blink.CaretBlinkStep
import com.intellij.openapi.editor.impl.caret.model.CaretCursor
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
 * The whole animation state of one editor: where the carets are heading, how they blink, and the caretCursor the painter
 * reads. Every transition returns a new instance with a higher [version], so a concurrent writer is always detectable.
 */
internal class CaretAnimationState private constructor(
  private val motion: CaretMotionMachine,
  private val blink: CaretBlinkMachine,
  private val repaintMetrics: CaretRepaintMetrics,
  private val caretCursor: CaretCursor,
  private val lastFrameAt: AnimationTimeMark,
  private val isRunning: Boolean,
  private val version: Long,
) {
  fun isMotionSettled(): Boolean {
    return motion.isSettled()
  }

  fun caretCursor(): CaretCursor {
    return caretCursor
  }

  fun isRunning(): Boolean {
    return isRunning
  }

  fun version(): Long {
    return version
  }

  fun repaintMetrics(): CaretRepaintMetrics {
    return repaintMetrics
  }

  /**
   * How long the previous frame actually took, floored at one frame interval so that a long pause does not make the
   * next frame jump.
   */
  fun frameDurationAt(now: AnimationTimeMark): Duration {
    val sinceLastFrame = now - lastFrameAt
    return sinceLastFrame.coerceAtLeast(CaretFrameInterval.MOVEMENT)
  }

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

  fun startBlink(): CaretAnimationState = next(blink = blink.start())

  fun stopBlink(): CaretAnimationState = next(blink = blink.stop())

  fun restartBlink(): CaretAnimationState = next(blink = blink.restart())

  fun withRepaintMetrics(repaintMetrics: CaretRepaintMetrics): CaretAnimationState {
    if (repaintMetrics == this.repaintMetrics) {
      return this
    }
    return next(repaintMetrics = repaintMetrics, caretCursor = caretCursor.withRepaintMetrics(repaintMetrics))
  }

  fun withEnabled(enabled: Boolean): CaretAnimationState {
    val nextCaretCursor = caretCursor.withEnabled(enabled)
    return if (nextCaretCursor === caretCursor) this else next(caretCursor = nextCaretCursor)
  }

  fun withShown(shown: Boolean, now: AnimationTimeMark): CaretAnimationState {
    val nextBlink = if (shown) blink.start() else blink.stop()
    val nextCaretCursor = caretCursor.withShown(shown, now)
    return next(blink = nextBlink, caretCursor = nextCaretCursor)
  }

  fun showFullyOpaque(): CaretAnimationState = next(caretCursor = caretCursor.shownFullyOpaque())

  fun withActivityAt(activityAt: AnimationTimeMark): CaretAnimationState =
    next(caretCursor = caretCursor.withActivityAt(activityAt))

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
    val movedLocations = if (motionStep.moved) nextMotion.locations() else null
    val nextCaretCursor = caretCursor.withStep(movedLocations, blinkStep.opacity, tick.now(), repaintMetrics)
    val nextState = next(
      motion = nextMotion,
      blink = nextBlink,
      caretCursor = nextCaretCursor,
      lastFrameAt = tick.now(),
    )
    val step = stepFor(motionStep, blinkStep, nextCaretCursor, nextState.version)
    return nextState to step
  }

  private fun stepFor(
    motionStep: CaretMotionStep,
    blinkStep: CaretBlinkStep,
    nextCaretCursor: CaretCursor,
    version: Long,
  ): CaretStep {
    val opacityChanged = nextCaretCursor.opacityDiffersFrom(caretCursor)
    return CaretStep(
      moved = motionStep.moved,
      opacityChanged = opacityChanged,
      prefetch = prefetchFor(motionStep, blinkStep, nextCaretCursor),
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
    nextCaretCursor: CaretCursor,
  ): List<CaretRectangle>? {
    val motionPrefetch = motionStep.prefetch
    if (motionPrefetch != null) {
      return motionPrefetch
    }
    val canPrefetchBlink = blinkStep.wantsPrefetch && nextCaretCursor.locations().isNotEmpty()
    return if (canPrefetchBlink) {
      nextCaretCursor.locations()
    } else {
      null
    }
  }

  private fun next(
    motion: CaretMotionMachine = this.motion,
    blink: CaretBlinkMachine = this.blink,
    repaintMetrics: CaretRepaintMetrics = this.repaintMetrics,
    caretCursor: CaretCursor = this.caretCursor,
    lastFrameAt: AnimationTimeMark = this.lastFrameAt,
    isRunning: Boolean = this.isRunning,
  ): CaretAnimationState {
    return CaretAnimationState(motion, blink, repaintMetrics, caretCursor, lastFrameAt, isRunning, version + 1)
  }

  companion object {
    fun initial(): CaretAnimationState = CaretAnimationState(
      motion = CaretMotionMachine.DORMANT,
      blink = CaretBlinkMachine.DORMANT,
      repaintMetrics = CaretRepaintMetrics.EMPTY,
      caretCursor = CaretCursor.INITIAL,
      lastFrameAt = AnimationClock.now(),
      isRunning = false,
      version = 0L,
    )
  }
}
