// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import java.awt.geom.Point2D
import kotlin.math.hypot

internal class CaretTrajectory private constructor(
  val target: CaretPlacement,
  val position: Point2D,
  private val startPos: Point2D,
  private val velocity: Velocity,
) {
  val distanceToTarget: Double get() = hypot(target.x - position.x, target.y - position.y)

  fun rectangle(): CaretRectangle = target.rectangleAt(position)

  fun eased(ease: Double): CaretTrajectory {
    val eased = positionAt(ease)
    return CaretTrajectory(target, eased, startPos, Velocity.between(position, eased))
  }

  fun pursued(approachFactor: Double, damping: Double): CaretTrajectory {
    val damped = velocity.damped(damping)
    val rawX = position.x + (target.x - position.x) * approachFactor + damped.dx
    val rawY = position.y + (target.y - position.y) * approachFactor + damped.dy
    val overshotX = (target.x - position.x) * (target.x - rawX) < 0.0
    val overshotY = (target.y - position.y) * (target.y - rawY) < 0.0

    return CaretTrajectory(
      target = target,
      position = Point2D.Double(if (overshotX) target.x else rawX, if (overshotY) target.y else rawY),
      startPos = startPos,
      velocity = damped.stopped(alongX = overshotX, alongY = overshotY),
    )
  }

  fun atTarget(): CaretTrajectory = CaretTrajectory(target, target.toPoint(), startPos, velocity)

  fun aimedAt(next: CaretPlacement): CaretTrajectory = CaretTrajectory(next, position, startPos, velocity)

  fun restartedAt(next: CaretPlacement): CaretTrajectory = CaretTrajectory(next, position, position, Velocity.ZERO)

  override fun toString(): String =
    "CaretTrajectory(target=$target, position=$position, startPos=$startPos, velocity=$velocity)"

  private fun positionAt(ease: Double): Point2D = when {
    ease >= 1.0 -> target.toPoint()
    else -> Point2D.Double(
      startPos.x + (target.x - startPos.x) * ease,
      startPos.y + (target.y - startPos.y) * ease,
    )
  }

  companion object {
    fun restingAt(placement: CaretPlacement): CaretTrajectory =
      CaretTrajectory(placement, placement.toPoint(), placement.toPoint(), Velocity.ZERO)
  }
}
