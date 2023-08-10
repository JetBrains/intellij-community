// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.project.*
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent

open class PlatformFrameTitleBuilder : FrameTitleBuilder() {
  override fun getProjectTitle(project: Project): String {
    val basePath: @SystemIndependent @NonNls String = project.basePath ?: return project.name
    val projects = ProjectManager.getInstance().openProjects
    val sameNamedProjects = projects.count { it.name == project.name }
    if (sameNamedProjects == 1 && !UISettings.getInstance().fullPathsInWindowHeader) {
      return project.name
    }

    return if (basePath == project.name && !UISettings.getInstance().fullPathsInWindowHeader) {
      "[${FileUtil.getLocationRelativeToUserHome(basePath)}]"
    }
    else {
      "${project.name} [${FileUtil.getLocationRelativeToUserHome(basePath)}]"
    }
  }

  override fun getFileTitle(project: Project, file: VirtualFile): String {
    val overriddenTitle = VfsPresentationUtil.getCustomPresentableNameForUI(project, file)
    return when {
      overriddenTitle != null -> overriddenTitle
      file.parent == null -> file.presentableName
      UISettings.getInstance().fullPathsInWindowHeader -> {
        displayUrlRelativeToProject(file = file,
                                    url = file.presentableUrl,
                                    project = project,
                                    isIncludeFilePath = true,
                                    moduleOnTheLeft = false)
      }
      else -> {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        if (!fileIndex.isInContent(file)) {
          val pathWithLibrary = decorateWithLibraryName(file, project, file.presentableName)
          return pathWithLibrary ?: FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
        }
        val fileTitle = VfsPresentationUtil.getPresentableNameForUI(project, file)
        if (PlatformUtils.isCidr() || PlatformUtils.isRider()) {
          fileTitle
        }
        else {
          appendModuleName(file = file, project = project, result = fileTitle, moduleOnTheLeft = false)
        }
      }
    }
  }
}