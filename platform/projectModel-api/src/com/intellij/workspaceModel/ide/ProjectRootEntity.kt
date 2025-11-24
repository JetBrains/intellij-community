// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.nio.file.Path
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
suspend fun registerProjectRoot(project: Project, projectDir: VirtualFileUrl) {
  val workspaceModel = project.serviceAsync<WorkspaceModel>()
  workspaceModel.update("Add project root ${projectDir.presentableUrl} to project ${project.name}") { storage ->
    val entity = ProjectRootEntity(projectDir, ProjectRootEntitySource)
    if (storage.entities<ProjectRootEntity>().none { it.root == entity.root }) storage.addEntity(entity)
  }
}

@Internal
suspend fun registerProjectRoot(project: Project, projectDir: Path) {
  val workspaceModel = project.serviceAsync<WorkspaceModel>()
  val projectBaseDirUrl = projectDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
  registerProjectRoot(project, projectBaseDirUrl)
}

@Internal
suspend fun unregisterProjectRoot(project: Project, projectRoot: VirtualFileUrl) {
  val workspaceModel = project.serviceAsync<WorkspaceModel>()
  workspaceModel.update("Remove project root ${projectRoot.presentableUrl} from project ${project.name}") { storage ->
    val entity = storage.entities(ProjectRootEntity::class.java).firstOrNull { it.root == projectRoot } ?: return@update
    storage.removeEntity(entity)
  }
}

/**
 * Non-suspend alternative to [unregisterProjectRoot]
 */
@Internal
fun unregisterProjectRootBlocking(project: Project, projectDir: VirtualFileUrl) {
  val workspaceModel = WorkspaceModel.getInstance(project)
  ApplicationManager.getApplication().runWriteAction {
    workspaceModel.updateProjectModel("Remove project root ${projectDir.presentableUrl} from project ${project.name}") { storage ->
      val entity = storage.entities<ProjectRootEntity>().firstOrNull { it.root == projectDir } ?: return@updateProjectModel
      storage.removeEntity(entity)
    }
  }
}

/**
 * Non-suspend alternative
 */
@Internal
fun registerProjectRootBlocking(project: Project, projectDir: Path) {
  val workspaceModel = WorkspaceModel.getInstance(project)
  val projectBaseDirUrl = projectDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
  ApplicationManager.getApplication().runWriteAction {
    workspaceModel.updateProjectModel("Add project root $projectDir to project ${project.name}") { storage ->
      val entity = ProjectRootEntity(projectBaseDirUrl, ProjectRootEntitySource)
      if (storage.entities<ProjectRootEntity>().none { it.root == entity.root }) storage.addEntity(entity)
    }
  }
}

@Internal
object ProjectRootEntitySource : EntitySource

/**
 * Used for creating initial state for Files view. [root] will be the root that Files view display
 */
@Internal
interface ProjectRootEntity : WorkspaceEntity {
  val root: VirtualFileUrl
}
