// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent

@ApiStatus.Internal
@Serializable
@ConsistentCopyVisibility
data class RepositoryId private constructor(
  val projectId: ProjectId,
  /**
   * Normalized, system-independent path to the repository root uniquely identifying this repository in the project.
   */
  private val rootPath: @SystemIndependent String,
) {
  companion object {
    fun from(projectId: ProjectId, rootPath: FilePath): RepositoryId = RepositoryId(
      projectId = projectId,
      rootPath = rootPath.path
    )

    fun from(projectId: ProjectId, rootPath: VirtualFile): RepositoryId = RepositoryId(
      projectId = projectId,
      rootPath = rootPath.path
    )
  }
}