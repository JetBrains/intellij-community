// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
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


internal class AutoExcludeInContentRootsWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
  override val entityClass: Class<ContentRootEntity>
    get() = ContentRootEntity::class.java

  override val dependenciesOnOtherEntities: List<DependencyDescription<ContentRootEntity>>
    get() = listOf(DependencyDescription.OnParent(ModuleEntity::class.java) { it.contentRoots.asSequence() })

  override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val directories = Registry.get("ide.workspace.model.relative.paths.to.exclude.automatically").asString()
      .split(";").map { it.trim() }.filter { it.isNotEmpty() && it != "." }

    if (entity.module.type?.name != "JAVA_MODULE") { // There can be too many java modules in a project. Also, java projects should be covered by ProjectRootEntity
      for (dir in directories) {
        registrar.registerExcludedRoot(entity.url.append(dir), entity)
      }
    }
  }
}