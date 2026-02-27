// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.model

import com.intellij.openapi.project.Project

internal data class ProjectEntry(
  val path: String,
  val name: String,
  val project: Project?,
  val branch: String? = null,
  val worktreeEntries: List<WorktreeEntry> = emptyList(),
)

internal data class WorktreeEntry(
  val path: String,
  val name: String,
  val branch: String?,
  val project: Project?,
)
