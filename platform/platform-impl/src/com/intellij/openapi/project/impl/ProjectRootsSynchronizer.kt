// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProjectRootsSynchronizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Registry.`is`("ide.create.project.root.entity")) return

    val projectRootsComponent = project.serviceAsync<ProjectRootPersistentStateComponent>()
    val roots = projectRootsComponent.projectRootUrls
    val virtualFileUrlManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()
    for (root in roots) {
      registerProjectRoot(project, virtualFileUrlManager.getOrCreateFromUrl(root))
    }

    launchListener(project, projectRootsComponent.scope)
  }

  /**
   * Keeps [ProjectRootPersistentStateComponent] in sync with the actual ProjectRootEntities in the workspace model.
   */
  private fun launchListener(project: Project, scope: CoroutineScope) {

    scope.launch {
      val workspaceModel = project.serviceAsync<WorkspaceModel>()
      val flow = workspaceModel.eventLog
        .filter { change -> change.getChanges(ProjectRootEntity::class.java).isNotEmpty() }

      val component = project.serviceAsync<ProjectRootPersistentStateComponent>()
      component.projectRootUrls = workspaceModel.currentSnapshot.entities<ProjectRootEntity>().map { it.root.url }.toList()

      flow.collect { change ->
        val newRoots = change.storageAfter.entities<ProjectRootEntity>().map { it.root.url }.toList()
        val component = project.serviceAsync<ProjectRootPersistentStateComponent>()
        component.projectRootUrls = newRoots.toList()
      }
    }
  }
}
