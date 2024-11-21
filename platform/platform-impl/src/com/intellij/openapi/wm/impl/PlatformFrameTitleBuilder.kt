// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil.getCustomEditorTabTitle
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil.getCustomEditorTabTitleAsync
import com.intellij.openapi.project.*
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.util.PathUtil
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

  override suspend fun getFileTitleAsync(project: Project, file: VirtualFile): String {
    val overriddenTitle = getCustomEditorTabTitleAsync(project, file)
    return readAction {
      doGetFileTitle(
        overriddenTitle = overriddenTitle,
        file = file,
        project = project,
      )
    }
  }

  override fun getFileTitle(project: Project, file: VirtualFile): String {
    return doGetFileTitle(
      overriddenTitle = getCustomEditorTabTitle(project, file),
      file = file,
      project = project,
    )
  }
}

private fun doGetFileTitle(
  overriddenTitle: @NlsContexts.TabTitle String?,
  file: VirtualFile,
  project: Project,
): String {
  return when {
    overriddenTitle != null -> overriddenTitle
    PathUtil.getParentPath(file.path).isEmpty() -> file.presentableName
    UISettings.getInstance().fullPathsInWindowHeader && !ExperimentalUI.isNewUI() -> {
      displayUrlRelativeToProject(
        file = file,
        url = file.presentableUrl,
        project = project,
        isIncludeFilePath = true,
        moduleOnTheLeft = false,
      )
    }
    UISettings.getInstance().fullPathsInWindowHeader -> file.presentableUrl
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