// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface GradleExternalProjectEntity : WorkspaceEntity {
  @Parent
  val externalProject: ExternalProjectEntity
  val gradleVersion: String
}

val ExternalProjectEntity.gradleInfo: GradleExternalProjectEntity
  by WorkspaceEntity.extension()