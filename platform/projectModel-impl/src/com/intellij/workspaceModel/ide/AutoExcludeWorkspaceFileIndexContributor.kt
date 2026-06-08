// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

/**
 * Exclude worktrees created by AI agents.
 */
internal class AutoExcludeWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ProjectRootEntity> {
  override val entityClass: Class<ProjectRootEntity>
    get() = ProjectRootEntity::class.java

  override fun registerFileSets(entity: ProjectRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val directories = Registry.get("ide.workspace.model.relative.paths.to.exclude.automatically").asString()
      .split(";").map { it.trim() }.filter { it.isNotEmpty() && it != "." }

    for (dir in directories) {
      registrar.registerExcludedRoot(entity.root.append(dir), entity)
    }
  }
}
