// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

/**
 * Provides connection between [ModuleEntity] and [GradleProjectEntity]
 */
interface GradleModuleEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity
  val gradleProjectId: GradleProjectEntityId
}

val ModuleEntity.gradleModuleEntity: GradleModuleEntity?
  by WorkspaceEntity.extension()
