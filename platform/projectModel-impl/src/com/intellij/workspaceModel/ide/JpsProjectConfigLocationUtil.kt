// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import org.jetbrains.annotations.ApiStatus

/**
 * Returns `null` for the default project
 */
@ApiStatus.Internal
fun getJpsProjectConfigLocation(project: Project): JpsProjectConfigLocation? {
  val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
  return if (project.isDirectoryBased) {
    project.basePath?.let {
      val ideaFolder = project.stateStore.directoryStorePath!!.toVirtualFileUrl(virtualFileUrlManager)
      val baseUrl = VfsUtilCore.pathToUrl(it)
      JpsProjectConfigLocation.DirectoryBased(virtualFileUrlManager.getOrCreateFromUrl(baseUrl), ideaFolder)
    }
  }
  else {
    project.projectFilePath?.let {
      val projectFileUrl = VfsUtilCore.pathToUrl(it)
      val iprFile = virtualFileUrlManager.getOrCreateFromUrl(projectFileUrl)
      JpsProjectConfigLocation.FileBased(iprFile, iprFile.parent!!)
    }
  }
}