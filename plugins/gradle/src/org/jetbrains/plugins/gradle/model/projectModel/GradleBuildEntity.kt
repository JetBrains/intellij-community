// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface GradleBuildEntity : WorkspaceEntityWithSymbolicId {
  @Parent
  val externalProject: ExternalProjectEntity
  val externalProjectId: ExternalProjectEntityId

  val name: String
  // URL of the directory containing the settings.gradle(.kts)
  val url: VirtualFileUrl
  val projects: List<GradleProjectEntity>

  override val symbolicId: GradleBuildEntityId
    get() = GradleBuildEntityId(externalProjectId, url)
}

val ExternalProjectEntity.gradleBuilds: List<GradleBuildEntity>
  by WorkspaceEntity.extension()