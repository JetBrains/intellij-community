// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import java.awt.geom.Point2D

internal class CaretTrajectory private constructor(
  val target: CaretPlacement,
  private val position: Point2D,
  private val startPos: Point2D,
  private val velocity: Velocity,
) {
  val distanceToTarget: Double get() = target.distanceTo(position)

  fun rectangle(): CaretRectangle = target.rectangleAt(position)

  fun rectangleAt(ease: Double): CaretRectangle {
    val easedPosition = positionAt(ease)
    return target.rectangleAt(easedPosition)
  }

  /**
   * Interpolates towards the target along the shared easing curve, at progress [ease].
   */
  fun eased(ease: Double): CaretTrajectory {
    val easedPosition = positionAt(ease)
    val easedVelocity = Velocity.between(position, easedPosition)
    return CaretTrajectory(target, easedPosition, startPos, easedVelocity)
  }

  /**
   * Closes [approachFactor] of the remaining distance and adds the inherited velocity, without passing the target.
   */
  fun pursued(approachFactor: Double, damping: Double): CaretTrajectory {
    val dampedVelocity = velocity.damped(damping)
    val raw = rawPositionAfter(approachFactor, dampedVelocity)
    val overshotX = didOvershoot(target.x(), position.x, raw.x)
    val overshotY = didOvershoot(target.y(), position.y, raw.y)
    val nextX = if (overshotX) target.x() else raw.x
    val nextY = if (overshotY) target.y() else raw.y
    return CaretTrajectory(
      target = target,
      position = Point2D.Double(nextX, nextY),
      startPos = startPos,
      velocity = dampedVelocity.stopped(alongX = overshotX, alongY = overshotY),
    )
  }

  fun atTarget(): CaretTrajectory = CaretTrajectory(target, target.toPoint(), startPos, velocity)

  /**
   * Keeps the current position and velocity, and aims at a new target mid-flight.
   */
  fun aimedAt(next: CaretPlacement): CaretTrajectory = CaretTrajectory(next, position, startPos, velocity)

  /**
   * Treats the current position as a fresh start, so a new easing curve begins from here.
   */
  fun restartedAt(next: CaretPlacement): CaretTrajectory =
    CaretTrajectory(next, position, position, Velocity.ZERO)

  override fun toString(): String =
    "CaretTrajectory(target=$target, position=$position, startPos=$startPos, velocity=$velocity)"

  /**
   * Where one pursuit step lands before the result is clamped to the target.
   */
  private fun rawPositionAfter(approachFactor: Double, dampedVelocity: Velocity): Point2D {
    val remainingX = target.x() - position.x
    val remainingY = target.y() - position.y
    val approachedX = position.x + remainingX * approachFactor
    val approachedY = position.y + remainingY * approachFactor
    val approached = Point2D.Double(approachedX, approachedY)
    return dampedVelocity.appliedTo(approached)
  }

  private fun positionAt(ease: Double): Point2D {
    if (ease >= 1.0) {
      return target.toPoint()
    }
    val travelX = target.x() - startPos.x
    val travelY = target.y() - startPos.y
    return Point2D.Double(startPos.x + travelX * ease, startPos.y + travelY * ease)
  }

  companion object {
    fun restingAt(placement: CaretPlacement): CaretTrajectory {
      val position = placement.toPoint()
      return CaretTrajectory(placement, position, position, Velocity.ZERO)
    }
  }
}

/**
 * Whether a step from [from] towards [target] ended up on the far side of it.
 */
private fun didOvershoot(target: Double, from: Double, reached: Double): Boolean {
  return (target - from) * (target - reached) < 0.0
}
