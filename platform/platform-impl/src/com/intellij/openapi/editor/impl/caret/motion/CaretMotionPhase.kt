// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.caret.model.CaretSettings
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.view.animation.AnimationClock
import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import kotlin.time.Duration

internal sealed interface CaretMotionPhase {
  fun trajectories(): Map<Caret, CaretTrajectory>

  fun settling(): Settling

  fun isEasing(): Boolean

  fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase

  fun advance(tick: CaretTick, timeConstant: Duration): CaretMotionPhase

  fun plannedFrames(settings: CaretSettings): List<CaretRectangle>

  /**
   * Whether every caret has come to rest, so no further frame is needed.
   */
  fun isSettled(): Boolean {
    return settling().isComplete()
  }

  /**
   * Where every caret is painted right now.
   */
  fun locations(): List<CaretRectangle> {
    return trajectories().values.map { trajectory -> trajectory.rectangle() }
  }

  /**
   * Where every caret is heading.
   */
  fun targets(): List<CaretPlacement> {
    return trajectories().values.map { trajectory -> trajectory.target }
  }

  /**
   * Every frame this easing move will paint, or `null` when there is nothing worth prefetching into the cache.
   */
  fun framesWorthPrefetching(settings: CaretSettings): List<CaretRectangle>? {
    val hasEasingInProgress = trajectories().isNotEmpty() && isEasing() && !isSettled()
    return if (hasEasingInProgress) {
      plannedFrames(settings)
    } else {
      null
    }
  }

  override fun toString(): String

  /**
   * Interpolates every caret from its own fixed start towards its own target along one shared easing curve, and
   * finishes deterministically for all of them after the configured duration. Entered whenever a move starts from rest.
   */
  data class Easing(
    private val trajectories: Map<Caret, CaretTrajectory>,
    private val startTime: AnimationTimeMark,
    private val settling: Settling = Settling.RESTLESS,
  ) : CaretMotionPhase {
    override fun isEasing(): Boolean = true
    override fun trajectories(): Map<Caret, CaretTrajectory> = trajectories
    override fun settling(): Settling = settling

    override fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase {
      return Easing(trajectories, startTime, settling)
    }

    override fun advance(tick: CaretTick, timeConstant: Duration): CaretMotionPhase {
      val settings = tick.settings()
      val elapsed = tick.elapsedSince(startTime)
      val eased = easedTrajectories(elapsed, settings)
      val finished = elapsed >= settings.moveDuration()
      val settled = if (finished) Settling.COMPLETE else settling.after(eased.residualDistance())
      return Easing(eased.rested(settled), startTime, settled)
    }

    override fun plannedFrames(settings: CaretSettings): List<CaretRectangle> {
      val frameCount = settings.easingFrameCount()
      return (0..frameCount).flatMap { frame -> rectanglesAtFrame(frame, frameCount, settings) }
    }

    private fun easedTrajectories(elapsed: Duration, settings: CaretSettings): Map<Caret, CaretTrajectory> {
      val easingTime = snappedEasingTime(elapsed, settings)
      val ease = settings.easing().apply(easingTime)
      return trajectories.mapValues { (_, trajectory) -> trajectory.eased(ease) }
    }

    private fun rectanglesAtFrame(
      frame: Int,
      frameCount: Int,
      settings: CaretSettings,
    ): List<CaretRectangle> {
      val progress = frame.toDouble() / frameCount
      val ease = settings.easing().apply(progress)
      return trajectories.values.map { trajectory -> trajectory.rectangleAt(ease) }
    }
  }

  /**
   * Closes a constant fraction of the *remaining* distance of every caret each tick, plus the velocity each of them
   * inherited. Has no start, no duration and no fixed end, so the targets may move mid-flight and the group just bends
   * towards them.
   */
  data class Pursuit(
    private val trajectories: Map<Caret, CaretTrajectory>,
    private val settling: Settling = Settling.RESTLESS,
  ) : CaretMotionPhase {
    override fun trajectories(): Map<Caret, CaretTrajectory> = trajectories
    override fun settling(): Settling = settling
    override fun isEasing(): Boolean = false

    override fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase {
      return Pursuit(trajectories, settling)
    }

    override fun advance(tick: CaretTick, timeConstant: Duration): CaretMotionPhase {
      val approachFactor = tick.approachFactor(timeConstant)
      val damping = tick.velocityDamping()
      val pursued = trajectories.mapValues {
        (_, trajectory) -> trajectory.pursued(approachFactor, damping)
      }
      val settled = settling.after(pursued.residualDistance())
      return Pursuit(pursued.rested(settled), settled)
    }

    override fun plannedFrames(settings: CaretSettings): List<CaretRectangle> {
      return emptyList()
    }
  }

  companion object {
    val DORMANT: CaretMotionPhase = Easing(
      trajectories = emptyMap(),
      startTime = AnimationClock.now(),
      settling = Settling.COMPLETE,
    )
  }
}

/**
 * Easing progress quantised to whole frames, so that the frames prefetched into the cache are the frames painted.
 */
private fun snappedEasingTime(elapsed: Duration, settings: CaretSettings): Double {
  val frameCount = settings.easingFrameCount()
  val progress = (elapsed / settings.moveDuration()).coerceIn(0.0, 1.0)
  val elapsedFrames = (progress * frameCount).toInt()
  return elapsedFrames / frameCount.toDouble()
}

private fun Map<Caret, CaretTrajectory>.residualDistance(): Double {
  val furthestDistance = values.maxOfOrNull {
    trajectory -> trajectory.distanceToTarget
  }
  return furthestDistance ?: 0.0
}

private fun Map<Caret, CaretTrajectory>.rested(settling: Settling): Map<Caret, CaretTrajectory> {
  return if (settling.isComplete()) {
    mapValues { (_, trajectory) -> trajectory.atTarget() }
  } else {
    this
  }
}
