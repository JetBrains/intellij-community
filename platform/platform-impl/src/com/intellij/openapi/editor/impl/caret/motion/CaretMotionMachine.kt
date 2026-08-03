// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.impl.caret.model.CaretClock
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.caret.model.TICK_MS
import kotlin.math.max
import kotlin.time.Duration

private const val MIN_URGENCY = 0.2
private const val URGENCY_DECAY = 0.8

internal class CaretMotionMachine {
  private var phase: CaretMotionPhase = CaretMotionPhase.DORMANT
  private var stale: List<CaretRectangle> = emptyList()
  private var urgency = 1.0
  private var dirty = false

  val isSettled: Boolean get() = phase.settling.isComplete

  fun retarget(placements: List<CaretPlacement>, tick: CaretTick) {
    val snapping = phase.settling.isComplete && (holdsSamePlaces(placements) || !tick.isCaretShown)

    urgency = when {
      snapping || holdsSameTargets(placements) -> urgency
      else -> max(MIN_URGENCY, urgency * URGENCY_DECAY)
    }
    aimAt(placements, tick, snapping)
  }

  fun snapTo(placements: List<CaretPlacement>, tick: CaretTick) {
    urgency = 1.0
    aimAt(placements, tick, snapping = true)
  }

  fun settle(tick: CaretTick) {
    urgency = 1.0
    phase = restingPhase(phase.trajectories.values.map { it.target }, tick)
    dirty = true
  }

  fun advance(tick: CaretTick, prefetching: Boolean): CaretMotionFrame {
    val timeConstantMs = max(TICK_MS.toDouble(), tick.settings.moveTimeConstantMs * urgency)
    val wasMoving = !phase.settling.isComplete
    val advancedPhase = if (wasMoving) phase.advance(tick, timeConstantMs) else phase
    val drained = stale
    val moving = !advancedPhase.settling.isComplete

    val frame = CaretMotionFrame(
      locations = if (dirty || wasMoving) advancedPhase.trajectories.values.map { it.rectangle() } else null,
      stale = drained,
      nextDelay = if (moving) CaretClock.TICK else Duration.INFINITE,
    )

    phase = advancedPhase
    stale = emptyList()
    dirty = false
    urgency = if (moving) urgency else 1.0

    return frame
  }

  private fun aimAt(placements: List<CaretPlacement>, tick: CaretTick, snapping: Boolean) {
    val previous = phase.trajectories
    val live = placements.mapTo(HashSet()) { it.caret }

    stale = stale + previous.filterKeys { it !in live }.values.map { it.rectangle() }
    phase = when {
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
    dirty = true
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

  private fun holdsSameSpots(placements: List<CaretPlacement>): Boolean =
    placements.isNotEmpty() && placements.all { phase.trajectories[it.caret]?.target?.isVisuallyAt(it) == true }

  private fun holdsSamePlaces(placements: List<CaretPlacement>): Boolean =
    phase.trajectories.isNotEmpty() && placements.all { phase.trajectories[it.caret]?.target?.isSamePlace(it) == true }

  private fun holdsSameTargets(placements: List<CaretPlacement>): Boolean =
    phase.trajectories.size == placements.size && placements.all { phase.trajectories[it.caret]?.target?.matches(it) == true }
}
