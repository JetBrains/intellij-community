// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.impl.caret.model.CaretFrameInterval
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import kotlin.math.max
import kotlin.time.Duration

/**
 * Drives every caret towards its target.
 *
 * Urgency shortens the move time constant, so that a caret retargeted mid-flight catches up faster than it started.
 * It decays with every interrupted move and resets once the motion settles.
 */
internal class CaretMotionMachine private constructor(
  private val phase: CaretMotionPhase,
  private val urgency: Double,
  private val retargeted: Boolean,
  private val framesToPrefetch: List<CaretRectangle>?,
) {
  fun isSettled(): Boolean {
    return phase.isSettled()
  }

  fun locations(): List<CaretRectangle> {
    return phase.locations()
  }

  fun retarget(placements: List<CaretPlacement>, tick: CaretTick, isCaretShown: Boolean): CaretMotionMachine {
    val isAtRest = phase.isSettled()
    val snapping = isAtRest && (holdsSamePlaces(placements) || !isCaretShown)
    val nextUrgency = urgencyAfterRetarget(placements, snapping)
    return withUrgency(nextUrgency).aimAt(placements, tick, snapping)
  }

  fun snapTo(placements: List<CaretPlacement>, tick: CaretTick): CaretMotionMachine {
    return withUrgency(FULL_URGENCY).aimAt(placements, tick, snapping = true)
  }

  /**
   * Drops the motion in progress and puts every caret at its target, for example when a bulk document update starts.
   */
  fun settle(tick: CaretTick): CaretMotionMachine {
    return CaretMotionMachine(
      phase = restingPhase(phase.targets(), tick),
      urgency = FULL_URGENCY,
      retargeted = true,
      framesToPrefetch = null,
    )
  }

  fun advance(tick: CaretTick, prefetching: Boolean): Pair<CaretMotionMachine, CaretMotionStep> {
    val wasMoving = !phase.isSettled()
    val advancedPhase = if (wasMoving) phase.advance(tick, timeConstantFor(tick)) else phase
    val moving = !advancedPhase.isSettled()
    val step = CaretMotionStep(
      moved = retargeted || wasMoving,
      prefetch = framesToPrefetch.takeIf { prefetching },
      nextDelay = if (moving) CaretFrameInterval.MOVEMENT else Duration.INFINITE,
    )
    val next = CaretMotionMachine(
      phase = advancedPhase,
      urgency = if (moving) urgency else FULL_URGENCY,
      retargeted = false,
      framesToPrefetch = framesToPrefetch.takeIf { moving },
    )
    return next to step
  }

  private fun timeConstantFor(tick: CaretTick): Duration {
    val scaledTimeConstant = tick.settings().moveTimeConstant() * urgency
    return scaledTimeConstant.coerceAtLeast(CaretFrameInterval.MOVEMENT)
  }

  /**
   * A move that replaces one already in flight is more urgent than the move it interrupted.
   */
  private fun urgencyAfterRetarget(placements: List<CaretPlacement>, snapping: Boolean): Double {
    val keepsUrgency = snapping || holdsSameTargets(placements)
    return if (keepsUrgency) {
      urgency
    } else {
      max(MIN_URGENCY, urgency * URGENCY_DECAY)
    }
  }

  private fun withUrgency(urgency: Double): CaretMotionMachine {
    return CaretMotionMachine(phase, urgency, retargeted, framesToPrefetch)
  }

  private fun aimAt(placements: List<CaretPlacement>, tick: CaretTick, snapping: Boolean): CaretMotionMachine {
    val nextPhase = phaseAimedAt(placements, tick, snapping)
    return CaretMotionMachine(
      phase = nextPhase,
      urgency = urgency,
      retargeted = true,
      framesToPrefetch = nextPhase.framesWorthPrefetching(tick.settings()),
    )
  }

  private fun phaseAimedAt(placements: List<CaretPlacement>, tick: CaretTick, snapping: Boolean): CaretMotionPhase {
    val previous = phase.trajectories()
    val isAtRest = phase.isSettled()
    return when {
      // The carets are already painted where they belong, so only the targets need rebasing.
      holdsSameSpots(placements) -> {
        val rebased = trajectoriesFrom(previous, placements, CaretTrajectory::aimedAt)
        phase.withTrajectories(rebased)
      }
      snapping || holdsSamePlaces(placements) -> {
        restingPhase(placements, tick)
      }
      isAtRest -> {
        // A move that starts from rest follows one shared easing curve from here.
        CaretMotionPhase.Easing(
          trajectories = trajectoriesFrom(previous, placements, CaretTrajectory::restartedAt),
          startTime = tick.now(),
        )
      }
      else -> {
        // A move interrupted mid-flight keeps its velocity and bends towards the new targets.
        CaretMotionPhase.Pursuit(
          trajectories = trajectoriesFrom(previous, placements, CaretTrajectory::aimedAt),
          settling = phase.settling(),
        )
      }
    }
  }

  private fun trajectoriesFrom(
    previous: Map<Caret, CaretTrajectory>,
    placements: List<CaretPlacement>,
    rebase: (CaretTrajectory, CaretPlacement) -> CaretTrajectory,
  ): Map<Caret, CaretTrajectory> {
    return placements.associate { placement ->
      placement.caret() to rebasedTrajectory(previous[placement.caret()], placement, rebase)
    }
  }

  private fun rebasedTrajectory(
    existing: CaretTrajectory?,
    placement: CaretPlacement,
    rebase: (CaretTrajectory, CaretPlacement) -> CaretTrajectory,
  ): CaretTrajectory {
    return if (existing == null) {
      CaretTrajectory.restingAt(placement)
    } else {
      rebase(existing, placement)
    }
  }

  private fun restingPhase(placements: List<CaretPlacement>, tick: CaretTick): CaretMotionPhase {
    val trajectories = placements.associate { placement ->
      placement.caret() to CaretTrajectory.restingAt(placement)
    }
    return CaretMotionPhase.Easing(trajectories, startTime = tick.now(), settling = Settling.COMPLETE)
  }

  private fun targetFor(placement: CaretPlacement): CaretPlacement? {
    return phase.trajectories()[placement.caret()]?.target
  }

  /**
   * Whether every placement is already painted where it belongs, whatever document position it now denotes.
   */
  private fun holdsSameSpots(placements: List<CaretPlacement>): Boolean {
    if (placements.isEmpty()) {
      return false
    }
    return placements.all { placement ->
      val target = targetFor(placement)
      target != null && target.isVisuallyAt(placement)
    }
  }

  /**
   * Whether every placement denotes the document position it already targeted, whatever pixel that is now.
   */
  private fun holdsSamePlaces(placements: List<CaretPlacement>): Boolean {
    if (phase.trajectories().isEmpty()) {
      return false
    }
    return placements.all { placement ->
      val target = targetFor(placement)
      target != null && target.isSamePlace(placement)
    }
  }

  /**
   * Whether no caret was added, removed or retargeted, so the move in progress needs no adjustment at all.
   */
  private fun holdsSameTargets(placements: List<CaretPlacement>): Boolean {
    if (phase.trajectories().size != placements.size) {
      return false
    }
    return placements.all { placement ->
      val target = targetFor(placement)
      target != null && target.matches(placement)
    }
  }

  companion object {
    val DORMANT: CaretMotionMachine = CaretMotionMachine(
      phase = CaretMotionPhase.DORMANT,
      urgency = FULL_URGENCY,
      retargeted = false,
      framesToPrefetch = null,
    )

    /**
     * The most urgency an interrupted move can accumulate.
     */
    private const val MIN_URGENCY = 0.2

    private const val URGENCY_DECAY = 0.8

    private const val FULL_URGENCY = 1.0
  }
}
