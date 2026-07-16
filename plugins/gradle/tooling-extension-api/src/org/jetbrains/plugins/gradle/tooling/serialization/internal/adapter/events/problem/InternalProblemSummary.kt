// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.ProblemId
import org.gradle.tooling.events.problems.ProblemSummary
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class InternalProblemSummary(private val id: ProblemId, private val count: Int) : Serializable, ProblemSummary {

  constructor(problemSummary: ProblemSummary) : this(InternalProblemId(problemSummary.problemId), problemSummary.count)

  override fun getProblemId(): ProblemId = id

  override fun getCount(): Int = count
}
