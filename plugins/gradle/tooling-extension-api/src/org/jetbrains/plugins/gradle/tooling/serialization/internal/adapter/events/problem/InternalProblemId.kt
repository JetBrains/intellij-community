// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.ProblemId
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class InternalProblemId(
  private val name: String,
  private val displayName: String,
  private val problemGroup: InternalProblemGroup,
) : Serializable, ProblemId {

  constructor(problemId: ProblemId) : this(problemId.name, problemId.displayName, InternalProblemGroup(problemId.group))

  override fun getName(): String = name

  override fun getDisplayName(): String = displayName

  override fun getGroup(): InternalProblemGroup = problemGroup
}
