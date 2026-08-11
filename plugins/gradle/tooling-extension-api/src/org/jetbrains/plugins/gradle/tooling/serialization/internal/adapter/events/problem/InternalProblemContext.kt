// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.Failure
import org.gradle.tooling.events.problems.Details
import org.gradle.tooling.events.problems.Location
import org.gradle.tooling.events.problems.ProblemContext
import org.gradle.tooling.events.problems.Solution
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalFailure
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.location.locationOf

@ApiStatus.Internal
class InternalProblemContext(
  private val myDetails: Details?,
  private val myOriginLocations: List<Location>,
  private val myContextualLocations: List<Location>,
  private val mySolutions: List<Solution>,
  private val myFailure: Failure?,
) : ProblemContext {

  constructor(context: ProblemContext) : this(
    InternalDetails(context.details!!),
    context.originLocations.mapNotNull { locationOf(it) },
    context.contextualLocations.mapNotNull { locationOf(it) },
    context.solutions.map { InternalSolution(it) },
    InternalFailure(context.failure!!)
  )

  override fun getDetails(): Details? = myDetails

  override fun getOriginLocations(): List<Location> = myOriginLocations

  override fun getContextualLocations(): List<Location> = myContextualLocations

  override fun getSolutions(): List<Solution> = mySolutions

  override fun getFailure(): Failure? = myFailure
}
