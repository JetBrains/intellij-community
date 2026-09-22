// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.impl.caret.model.CaretAnimationSettings
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.view.animation.AnimationClock
import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import java.awt.geom.Point2D
import kotlin.time.Duration

/// MARK: velocity and settling

internal class Velocity private constructor(private val dx: Double, private val dy: Double) {
  fun damped(factor: Double): Velocity = Velocity(dx * factor, dy * factor)

  fun stopped(alongX: Boolean, alongY: Boolean): Velocity {
    val nextDx = if (alongX) 0.0 else dx
    val nextDy = if (alongY) 0.0 else dy
    return Velocity(nextDx, nextDy)
  }

  /**
   * Carries [point] one tick further along this velocity.
   */
  fun appliedTo(point: Point2D): Point2D = Point2D.Double(point.x + dx, point.y + dy)

  override fun toString(): String = "Velocity(dx=$dx, dy=$dy)"

  companion object {
    val ZERO: Velocity = Velocity(0.0, 0.0)

    fun between(from: Point2D, to: Point2D): Velocity = Velocity(to.x - from.x, to.y - from.y)
  }
}

@JvmInline
internal value class Settling private constructor(private val ticks: Int) {
  val isComplete: Boolean get() = ticks >= SETTLE_TICKS

  fun after(distance: Double): Settling {
    val isNearTarget = distance < SETTLE_EPSILON
    return if (isNearTarget) {
      Settling(ticks + 1)
    } else {
      RESTLESS
    }
  }

  companion object {
    val RESTLESS: Settling = Settling(0)
    val COMPLETE: Settling = Settling(SETTLE_TICKS)

    /**
     * How many consecutive ticks a caret must stay within [SETTLE_EPSILON] of its target before it counts as settled.
     */
    private const val SETTLE_TICKS = 3

    private const val SETTLE_EPSILON = 0.25
  }
}

/// MARK: motion phases

internal sealed interface CaretMotionPhase {
  val trajectories: Map<Caret, CaretTrajectory>
  val settling: Settling
  val isEasing: Boolean

  /**
   * Whether every caret has come to rest, so no further frame is needed.
   */
  val isSettled: Boolean get() = settling.isComplete

  /**
   * Where every caret is painted right now.
   */
  val locations: List<CaretRectangle>
    get() {
      return trajectories.values.map { trajectory -> trajectory.rectangle() }
    }

  /**
   * Where every caret is heading.
   */
  val targets: List<CaretPlacement>
    get() {
      return trajectories.values.map { trajectory -> trajectory.target }
    }

  fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase

  fun advance(tick: CaretTick, timeConstant: Duration): CaretMotionPhase

  fun plannedFrames(settings: CaretAnimationSettings): List<CaretRectangle>

  override fun toString(): String

  /// MARK: easing

  /**
   * Interpolates every caret from its own fixed start towards its own target along one shared easing curve, and
   * finishes deterministically for all of them after the configured duration. Entered whenever a move starts from rest.
   */
  data class Easing(
    override val trajectories: Map<Caret, CaretTrajectory>,
    private val startTime: AnimationTimeMark,
    override val settling: Settling = Settling.RESTLESS,
  ) : CaretMotionPhase {
    override val isEasing: Boolean get() = true

    override fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase =
      Easing(trajectories, startTime, settling)

    override fun advance(tick: CaretTick, timeConstant: Duration): CaretMotionPhase {
      val settings = tick.settings
      val elapsed = tick.elapsedSince(startTime)
      val eased = easedTrajectories(elapsed, settings)
      val finished = elapsed >= settings.moveDuration
      val settled = if (finished) Settling.COMPLETE else settling.after(eased.residualDistance())
      return Easing(eased.rested(settled), startTime, settled)
    }

    override fun plannedFrames(settings: CaretAnimationSettings): List<CaretRectangle> {
      val frameCount = settings.easingFrameCount
      return (0..frameCount).flatMap { frame -> rectanglesAtFrame(frame, frameCount, settings) }
    }

    private fun easedTrajectories(elapsed: Duration, settings: CaretAnimationSettings): Map<Caret, CaretTrajectory> {
      val easingTime = snappedEasingTime(elapsed, settings)
      val ease = settings.easing.apply(easingTime)
      return trajectories.mapValues { (_, trajectory) -> trajectory.eased(ease) }
    }

    private fun rectanglesAtFrame(
      frame: Int,
      frameCount: Int,
      settings: CaretAnimationSettings,
    ): List<CaretRectangle> {
      val progress = frame.toDouble() / frameCount
      val ease = settings.easing.apply(progress)
      return trajectories.values.map { trajectory -> trajectory.rectangleAt(ease) }
    }
  }

  /// MARK: pursuit

  /**
   * Closes a constant fraction of the *remaining* distance of every caret each tick, plus the velocity each of them
   * inherited. Has no start, no duration and no fixed end, so the targets may move mid-flight and the group just bends
   * towards them.
   */
  data class Pursuit(
    override val trajectories: Map<Caret, CaretTrajectory>,
    override val settling: Settling = Settling.RESTLESS,
  ) : CaretMotionPhase {
    override val isEasing: Boolean get() = false

    override fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase =
      Pursuit(trajectories, settling)

    override fun advance(tick: CaretTick, timeConstant: Duration): CaretMotionPhase {
      val approachFactor = tick.approachFactor(timeConstant)
      val damping = tick.velocityDamping()
      val pursued = trajectories.mapValues { (_, trajectory) -> trajectory.pursued(approachFactor, damping) }
      val settled = settling.after(pursued.residualDistance())
      return Pursuit(pursued.rested(settled), settled)
    }

    override fun plannedFrames(settings: CaretAnimationSettings): List<CaretRectangle> = emptyList()
  }

  companion object {
    val DORMANT: CaretMotionPhase = Easing(
      trajectories = emptyMap(),
      startTime = AnimationClock.now(),
      settling = Settling.COMPLETE,
    )
  }
}

/// MARK: interpolation and settling helpers

/**
 * Easing progress quantised to whole frames, so that the frames prefetched into the cache are the frames painted.
 */
private fun snappedEasingTime(elapsed: Duration, settings: CaretAnimationSettings): Double {
  val frameCount = settings.easingFrameCount
  val progress = (elapsed / settings.moveDuration).coerceIn(0.0, 1.0)
  val elapsedFrames = (progress * frameCount).toInt()
  return elapsedFrames / frameCount.toDouble()
}

private fun Map<Caret, CaretTrajectory>.residualDistance(): Double {
  val furthestDistance = values.maxOfOrNull { trajectory -> trajectory.distanceToTarget }
  return furthestDistance ?: 0.0
}

private fun Map<Caret, CaretTrajectory>.rested(settling: Settling): Map<Caret, CaretTrajectory> {
  return if (settling.isComplete) {
    mapValues { (_, trajectory) -> trajectory.atTarget() }
  } else {
    this
  }
}
