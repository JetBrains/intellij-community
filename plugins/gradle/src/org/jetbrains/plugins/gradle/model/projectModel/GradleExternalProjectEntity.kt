// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

interface GradleExternalProjectEntity : WorkspaceEntityWithSymbolicId {
  @Parent
  val externalProject: ExternalProjectEntity
  val externalProjectId: ExternalProjectEntityId

  val gradleVersion: String

  override val symbolicId: GradleExternalProjectEntityId
    get() = GradleExternalProjectEntityId(externalProjectId)
}

@Suppress("unused")
val ExternalProjectEntity.gradleInfo: GradleExternalProjectEntity
  by WorkspaceEntity.extension()