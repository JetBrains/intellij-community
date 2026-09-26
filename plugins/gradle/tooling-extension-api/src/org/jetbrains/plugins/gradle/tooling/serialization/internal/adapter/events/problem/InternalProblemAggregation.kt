// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.ProblemAggregation
import org.gradle.tooling.events.problems.ProblemContext
import org.gradle.tooling.events.problems.ProblemDefinition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InternalProblemAggregation(
  private val myProblemDefinition: ProblemDefinition,
  private val myProblemContexts: List<ProblemContext>,
) : ProblemAggregation {

  constructor(problemAggregation: ProblemAggregation) : this(
    InternalProblemDefinition(problemAggregation.definition),
    problemAggregation.problemContext.map { InternalProblemContext(it) }
  )

  override fun getDefinition(): ProblemDefinition = myProblemDefinition

  override fun getProblemContext(): List<ProblemContext> = myProblemContexts
}
