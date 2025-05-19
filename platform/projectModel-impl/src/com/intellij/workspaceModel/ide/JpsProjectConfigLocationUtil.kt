// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus

/**
 * Returns `null` for the default project
 */
@ApiStatus.Internal
@RequiresBlockingContext
fun getJpsProjectConfigLocation(project: Project): JpsProjectConfigLocation? {
  return getJpsProjectConfigLocation(project, WorkspaceModel.getInstance(project).getVirtualFileUrlManager())
}

@ApiStatus.Internal
fun getJpsProjectConfigLocation(project: Project, virtualFileUrlManager: VirtualFileUrlManager): JpsProjectConfigLocation? {
  if (project.isDirectoryBased) {
    return project.basePath?.let {
      val ideaFolder = project.stateStore.directoryStorePath!!.toVirtualFileUrl(virtualFileUrlManager)
      val baseUrl = VfsUtilCore.pathToUrl(it)
      JpsProjectConfigLocation.DirectoryBased(virtualFileUrlManager.getOrCreateFromUrl(baseUrl), ideaFolder)
    }
  }
  else {
    return project.projectFilePath?.let {
      val projectFileUrl = VfsUtilCore.pathToUrl(it)
      val iprFile = virtualFileUrlManager.getOrCreateFromUrl(projectFileUrl)
      JpsProjectConfigLocation.FileBased(iprFile, iprFile.parent!!)
    }
  }
}