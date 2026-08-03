// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.impl.caret.model.CaretAnimationSettings
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import java.awt.geom.Point2D

private const val SETTLE_TICKS = 3
private const val SETTLE_EPSILON = 0.25

internal class Velocity private constructor(val dx: Double, val dy: Double) {
  fun damped(factor: Double): Velocity = Velocity(dx * factor, dy * factor)

  fun stopped(alongX: Boolean, alongY: Boolean): Velocity =
    Velocity(if (alongX) 0.0 else dx, if (alongY) 0.0 else dy)

  override fun toString(): String = "Velocity(dx=$dx, dy=$dy)"

  companion object {
    val ZERO: Velocity = Velocity(0.0, 0.0)

    fun between(from: Point2D, to: Point2D): Velocity = Velocity(to.x - from.x, to.y - from.y)
  }
}

@JvmInline
internal value class Settling private constructor(private val ticks: Int) {
  val isComplete: Boolean get() = ticks >= SETTLE_TICKS

  fun after(distance: Double): Settling = if (distance < SETTLE_EPSILON) Settling(ticks + 1) else RESTLESS

  companion object {
    val RESTLESS: Settling = Settling(0)
    val COMPLETE: Settling = Settling(SETTLE_TICKS)
  }
}

internal sealed interface CaretMotionPhase {
  val trajectories: Map<Caret, CaretTrajectory>
  val settling: Settling
  val isEasing: Boolean

  fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase

  fun advance(tick: CaretTick, timeConstantMs: Double): CaretMotionPhase

  override fun toString(): String

  /**
   * Interpolates every caret from its own fixed start towards its own target along one shared easing curve, and
   * finishes deterministically for all of them after the configured duration. Entered whenever a move starts from rest.
   */
  data class Easing(
    override val trajectories: Map<Caret, CaretTrajectory>,
    private val startTime: Long,
    override val settling: Settling = Settling.RESTLESS,
  ) : CaretMotionPhase {
    override val isEasing: Boolean get() = true

    override fun withTrajectories(trajectories: Map<Caret, CaretTrajectory>): CaretMotionPhase =
      Easing(trajectories, startTime, settling)

    override fun advance(tick: CaretTick, timeConstantMs: Double): CaretMotionPhase {
      val settings = tick.settings
      val elapsed = tick.elapsedSince(startTime)
      val finished = elapsed >= settings.moveDurationMs
      val ease = settings.easing.apply(snappedEasingTime(elapsed, settings))

      val eased = trajectories.mapValues { (_, trajectory) -> trajectory.eased(ease) }
      val settled = if (finished) Settling.COMPLETE else settling.after(eased.residualDistance())

      return Easing(eased.rested(settled), startTime, settled)
    }
  }

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

    override fun advance(tick: CaretTick, timeConstantMs: Double): CaretMotionPhase {
      val approachFactor = tick.approachFactor(timeConstantMs)
      val damping = tick.velocityDamping()

      val pursued = trajectories.mapValues { (_, trajectory) -> trajectory.pursued(approachFactor, damping) }
      val settled = settling.after(pursued.residualDistance())

      return Pursuit(pursued.rested(settled), settled)
    }
  }

  companion object {
    val DORMANT: CaretMotionPhase = Easing(emptyMap(), startTime = 0L, settling = Settling.COMPLETE)
  }
}

private fun snappedEasingTime(elapsedMs: Double, settings: CaretAnimationSettings): Double {
  val frameCount = settings.easingFrameCount
  val t = (elapsedMs / settings.moveDurationMs).coerceIn(0.0, 1.0)
  return (t * frameCount).toInt() / frameCount.toDouble()
}

private fun Map<Caret, CaretTrajectory>.residualDistance(): Double = values.maxOfOrNull { it.distanceToTarget } ?: 0.0

private fun Map<Caret, CaretTrajectory>.rested(settling: Settling): Map<Caret, CaretTrajectory> = when {
  settling.isComplete -> mapValues { (_, trajectory) -> trajectory.atTarget() }
  else -> this
}
