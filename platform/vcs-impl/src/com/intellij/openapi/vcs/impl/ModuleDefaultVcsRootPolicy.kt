// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.project.stateStore
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ModuleDefaultVcsRootPolicy(project: Project) : DefaultVcsRootPolicy(project) {
  init {
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, MyModulesListener())
  }

  override fun getDefaultVcsRoots(): Collection<VirtualFile> {
    val result = mutableSetOf<VirtualFile>()

    val baseDir = myProject.baseDir
    if (baseDir != null) {
      result.add(baseDir)

      val directoryStorePath = myProject.stateStore.directoryStorePath
      if (directoryStorePath != null) {
        val ideaDir = LocalFileSystem.getInstance().findFileByNioFile(directoryStorePath)
        if (ideaDir != null) {
          result.add(ideaDir)
        }
      }
    }

    result += runReadAction {
      WorkspaceModel.getInstance(myProject).currentSnapshot
        .entities(ContentRootEntity::class.java)
        .mapNotNull { it.url.virtualFile }
        .filter { it.isInLocalFileSystem }
        .filter { it.isDirectory }
    }
    return result
  }

  private inner class MyModulesListener : ContentRootChangeListener(skipFileChanges = true) {
    override fun contentRootsChanged(removed: List<VirtualFile>, added: List<VirtualFile>) {
      scheduleMappedRootsUpdate()
      scheduleRootsChangeProcessing(removed, added)
    }
  }
}