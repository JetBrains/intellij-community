// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events

import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.InternalProblem

@ApiStatus.Internal
class InternalSingleProblemEvent(
  eventTime: Long,
  displayName: String,
  descriptor: OperationDescriptor?,
  private val myProblem: Problem,
) : InternalProgressEvent(eventTime, displayName, descriptor), SingleProblemEvent {

  constructor(event: SingleProblemEvent, descriptor: InternalOperationDescriptor?) : this(
    event.eventTime,
    event.displayName,
    descriptor,
    InternalProblem(event.problem)
  )

  override fun getProblem(): Problem = myProblem
}
