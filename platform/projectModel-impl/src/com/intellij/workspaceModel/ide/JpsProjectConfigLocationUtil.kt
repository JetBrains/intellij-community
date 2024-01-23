// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore

/**
 * Returns `null` for the default project
 */
fun getJpsProjectConfigLocation(project: Project): JpsProjectConfigLocation? {
  return if (project.isDirectoryBased) {
    project.basePath?.let {
      val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
      val ideaFolder = project.stateStore.directoryStorePath!!.toVirtualFileUrl(virtualFileUrlManager)
      JpsProjectConfigLocation.DirectoryBased(virtualFileUrlManager.fromPath(it), ideaFolder)
    }
  }
  else {
    project.projectFilePath?.let {
      val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
      val iprFile = virtualFileUrlManager.fromPath(it)
      JpsProjectConfigLocation.FileBased(iprFile, iprFile.parent!!)
    }
  }
}