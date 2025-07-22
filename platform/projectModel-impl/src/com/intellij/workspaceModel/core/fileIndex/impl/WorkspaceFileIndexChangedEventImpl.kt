// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexChangedEvent
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class WorkspaceFileIndexChangedEventImpl(
  project: Project,
  private val removedFileSets: List<WorkspaceFileSet>,
  private val storedFileSets: List<WorkspaceFileSet>,
) : WorkspaceFileIndexChangedEvent(project) {
  override fun getRemovedFileSets(): Collection<WorkspaceFileSet> {
    return removedFileSets
  }

  override fun getStoredFileSets(): Collection<WorkspaceFileSet> {
    return storedFileSets
  }
}
