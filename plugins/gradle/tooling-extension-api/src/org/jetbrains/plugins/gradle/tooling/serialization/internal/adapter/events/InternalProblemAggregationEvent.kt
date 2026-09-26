// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events

import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.problems.ProblemAggregation
import org.gradle.tooling.events.problems.ProblemAggregationEvent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.InternalProblemAggregation

@ApiStatus.Internal
class InternalProblemAggregationEvent(
  eventTime: Long,
  displayName: String,
  descriptor: OperationDescriptor?,
  private val myProblemAggregation: ProblemAggregation,
) : InternalProgressEvent(eventTime, displayName, descriptor), ProblemAggregationEvent {

  constructor(event: ProblemAggregationEvent, descriptor: InternalOperationDescriptor?) :
    this(event.eventTime, event.displayName, descriptor, InternalProblemAggregation(event.problemAggregation))

  override fun getProblemAggregation(): ProblemAggregation = myProblemAggregation
}
