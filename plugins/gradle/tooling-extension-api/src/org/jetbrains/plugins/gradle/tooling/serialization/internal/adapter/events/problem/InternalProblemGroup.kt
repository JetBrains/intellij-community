// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.ProblemGroup
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class InternalProblemGroup(
  private val name: String,
  private val displayName: String,
  private val parent: InternalProblemGroup?,
) : Serializable, ProblemGroup {

  constructor(group: ProblemGroup) : this(
    group.name,
    group.displayName,
    if (group.parent == null) null else InternalProblemGroup(group.parent!!)
  )

  override fun getName(): String = name

  override fun getDisplayName(): String = displayName

  override fun getParent(): InternalProblemGroup? = parent
}
