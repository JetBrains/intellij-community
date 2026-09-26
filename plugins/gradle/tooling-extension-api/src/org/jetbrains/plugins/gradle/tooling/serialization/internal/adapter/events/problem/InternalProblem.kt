// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.Failure
import org.gradle.tooling.events.problems.AdditionalData
import org.gradle.tooling.events.problems.ContextualLabel
import org.gradle.tooling.events.problems.Details
import org.gradle.tooling.events.problems.Location
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.ProblemDefinition
import org.gradle.tooling.events.problems.Solution
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalFailure
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.location.locationOf
import java.io.Serializable

@ApiStatus.Internal
class InternalProblem(
  private val definition: ProblemDefinition,
  private val contextualLabel: ContextualLabel,
  private val details: Details,
  private val originLocations: List<Location>,
  private val contextualLocations: List<Location>,
  private val solutions: List<Solution>,
  private val failure: Failure?,
  private val additionalData: AdditionalData,
) : Serializable, Problem {

  constructor(problem: Problem) : this(
    InternalProblemDefinition(problem.definition),
    InternalContextualLabel(problem.contextualLabel),
    InternalDetails(problem.details),
    problem.originLocations?.let { toInternalLocation(it) } ?: emptyList(),
    problem.contextualLocations?.let { toInternalLocation(it) } ?: emptyList(),
    problem.solutions?.let { toInternalSolutions(it) } ?: emptyList(),
    if (problem.failure == null) null else InternalFailure(problem.failure!!),
    problem.additionalData?.let { InternalAdditionalData(it) } ?: InternalAdditionalData(emptyMap())
  )

  override fun getDefinition(): ProblemDefinition = definition

  override fun getContextualLabel(): ContextualLabel = contextualLabel

  override fun getDetails(): Details = details

  override fun getOriginLocations(): List<Location> = originLocations

  override fun getContextualLocations(): List<Location> = contextualLocations

  override fun getSolutions(): List<Solution> = solutions

  override fun getFailure(): Failure? = failure

  override fun getAdditionalData(): AdditionalData = additionalData

  companion object {
    private fun toInternalLocation(locations: List<Location>): List<Location> = locations.mapNotNull { locationOf(it) }

    private fun toInternalSolutions(solutions: List<Solution>): List<Solution> = solutions.map { InternalSolution(it) }
  }
}
