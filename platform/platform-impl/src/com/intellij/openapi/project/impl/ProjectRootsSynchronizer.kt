// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.registerProjectRoot
import com.intellij.workspaceModel.ide.unregisterProjectRoot
import kotlinx.coroutines.flow.filter
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

@Internal
class ProjectRootsSynchronizer : ProjectActivity {
  companion object {
    /**
     * Registers project roots from [ProjectRootPersistentStateComponent] to the workspace model
     */
    @VisibleForTesting
    suspend fun doRegister(project: Project) {
      val projectRootsComponent = project.serviceAsync<ProjectRootPersistentStateComponent>()
      val roots = projectRootsComponent.projectRootUrls
      val virtualFileUrlManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()
      for (root in roots) {
        registerProjectRoot(project, virtualFileUrlManager.storeAndGet(root))
      }
    }

    /**
     * Removes the project root of [projectDir] from the workspace model and from [ProjectRootPersistentStateComponent].
     *
     * `ProjectManagerImpl` adds both on every open, and [doRegister] puts the entity back from the component.
     * A caller that wants the root gone must therefore remove both.
     *
     * The model change goes last, so the listener of [ProjectRootsSynchronizer] rewrites the persisted list
     * from the model afterwards.
     */
    suspend fun doUnregister(project: Project, projectDir: Path) {
      val workspaceModel = project.serviceAsync<WorkspaceModel>()
      val url = projectDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
      val component = project.serviceAsync<ProjectRootPersistentStateComponent>()
      val isRegistered = workspaceModel.currentSnapshot.entities<ProjectRootEntity>().any { it.root == url }
      if (!isRegistered && !component.projectRootUrls.contains(url.url)) {
        return
      }
      component.removeProjectRoot(url.url)
      unregisterProjectRoot(project, url)
    }
  }

  override suspend fun execute(project: Project) {
    if (!Registry.`is`("ide.create.project.root.entity")) {
      return
    }

    doRegister(project)
    launchListener(project)
  }

  /**
   * Keeps [ProjectRootPersistentStateComponent] in sync with the actual ProjectRootEntities in the workspace model.
   */
  private suspend fun launchListener(project: Project) {
    val workspaceModel = project.serviceAsync<WorkspaceModel>()
    val flow = workspaceModel.eventLog
      .filter { change -> change.getChanges(ProjectRootEntity::class.java).isNotEmpty() }

    val component = project.serviceAsync<ProjectRootPersistentStateComponent>()
    component.projectRootUrls = workspaceModel.currentSnapshot.entities<ProjectRootEntity>().map { it.root.url }.toList()
    flow.collect { change ->
      component.projectRootUrls = change.storageAfter.entities<ProjectRootEntity>().map { it.root.url }.toList()
    }
  }
}
