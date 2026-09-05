// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.impl.caret.model.CaretAnimationSettings
import com.intellij.openapi.editor.impl.caret.model.CaretClock
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import kotlin.math.max
import kotlin.time.Duration

private const val MIN_URGENCY = 0.2
private const val URGENCY_DECAY = 0.8

internal class CaretMotionMachine private constructor(
  private val phase: CaretMotionPhase,
  private val urgency: Double,
  private val dirty: Boolean,
  private val currentEasingFrames: List<CaretRectangle>?,
) {
  val isSettled: Boolean get() = phase.settling.isComplete

  val locations: List<CaretRectangle> get() = phase.trajectories.values.map { it.rectangle() }

  /// MARK: motion transitions

  fun retarget(placements: List<CaretPlacement>, tick: CaretTick, isCaretShown: Boolean): CaretMotionMachine {
    val snapping = phase.settling.isComplete && (holdsSamePlaces(placements) || !isCaretShown)
    val nextUrgency = when {
      snapping || holdsSameTargets(placements) -> urgency
      else -> max(MIN_URGENCY, urgency * URGENCY_DECAY)
    }
    return withUrgency(nextUrgency).aimAt(placements, tick, snapping)
  }

  fun snapTo(placements: List<CaretPlacement>, tick: CaretTick): CaretMotionMachine =
    withUrgency(1.0).aimAt(placements, tick, snapping = true)

  fun settle(tick: CaretTick): CaretMotionMachine = CaretMotionMachine(
    phase = restingPhase(phase.trajectories.values.map { it.target }, tick),
    urgency = 1.0,
    dirty = true,
    currentEasingFrames = null,
  )

  /// MARK: frame updates

  fun advance(tick: CaretTick, prefetching: Boolean): Pair<CaretMotionMachine, CaretMotionStep> {
    val timeConstant = (tick.settings.moveTimeConstant * urgency).coerceAtLeast(CaretClock.MOVEMENT_FRAME)
    val wasMoving = !phase.settling.isComplete
    val advancedPhase = if (wasMoving) phase.advance(tick, timeConstant) else phase
    val moving = !advancedPhase.settling.isComplete

    val step = CaretMotionStep(
      moved = dirty || wasMoving,
      prefetch = currentEasingFrames.takeIf { prefetching },
      nextDelay = if (moving) CaretClock.MOVEMENT_FRAME else Duration.INFINITE,
    )
    val next = CaretMotionMachine(
      phase = advancedPhase,
      urgency = if (moving) urgency else 1.0,
      dirty = false,
      currentEasingFrames = currentEasingFrames.takeIf { moving },
    )
    return next to step
  }

  /// MARK: trajectory updates

  private fun withUrgency(urgency: Double): CaretMotionMachine =
    CaretMotionMachine(phase, urgency, dirty, currentEasingFrames)

  private fun aimAt(placements: List<CaretPlacement>, tick: CaretTick, snapping: Boolean): CaretMotionMachine {
    val previous = phase.trajectories
    val nextPhase = when {
      holdsSameSpots(placements) -> phase.withTrajectories(trajectoriesFrom(previous, placements, CaretTrajectory::aimedAt))
      snapping || holdsSamePlaces(placements) -> restingPhase(placements, tick)
      phase.settling.isComplete -> CaretMotionPhase.Easing(
        trajectories = trajectoriesFrom(previous, placements, CaretTrajectory::restartedAt),
        startTime = tick.now,
      )
      else -> CaretMotionPhase.Pursuit(
        trajectories = trajectoriesFrom(previous, placements, CaretTrajectory::aimedAt),
        settling = phase.settling,
      )
    }
    return CaretMotionMachine(nextPhase, urgency, true, nextPhase.easingFrames(tick.settings))
  }

  private fun trajectoriesFrom(
    previous: Map<Caret, CaretTrajectory>,
    placements: List<CaretPlacement>,
    rebase: (CaretTrajectory, CaretPlacement) -> CaretTrajectory,
  ): Map<Caret, CaretTrajectory> = placements.associate { placement ->
    val existing = previous[placement.caret]
    placement.caret to when (existing) {
      null -> CaretTrajectory.restingAt(placement)
      else -> rebase(existing, placement)
    }
  }

  private fun restingPhase(placements: List<CaretPlacement>, tick: CaretTick): CaretMotionPhase =
    CaretMotionPhase.Easing(
      trajectories = placements.associate { it.caret to CaretTrajectory.restingAt(it) },
      startTime = tick.now,
      settling = Settling.COMPLETE,
    )

  /// MARK: target comparisons

  private fun targetFor(placement: CaretPlacement) = phase.trajectories[placement.caret]?.target

  private fun holdsSameSpots(placements: List<CaretPlacement>): Boolean {
    val hasPlacements = placements.isNotEmpty()
    val allSameSpots by lazy { placements.all { targetFor(it)?.isVisuallyAt(it) == true } }

    return hasPlacements && allSameSpots
  }

  private fun holdsSamePlaces(placements: List<CaretPlacement>): Boolean {
    val hasTrajectories = phase.trajectories.isNotEmpty()
    val allSamePlaces by lazy { placements.all { targetFor(it)?.isSamePlace(it) == true } }

    return hasTrajectories && allSamePlaces
  }

  private fun holdsSameTargets(placements: List<CaretPlacement>): Boolean {
    val noNewCarets = phase.trajectories.size == placements.size
    val allSameTargets by lazy { placements.all { targetFor(it)?.matches(it) == true } }

    return noNewCarets && allSameTargets
  }

  companion object {
    val DORMANT: CaretMotionMachine = CaretMotionMachine(CaretMotionPhase.DORMANT, 1.0, false, null)
  }
}

private fun CaretMotionPhase.easingFrames(settings: CaretAnimationSettings): List<CaretRectangle>? = when {
  trajectories.isNotEmpty() && isEasing && !settling.isComplete -> framesTo(settings)
  else -> null
}
