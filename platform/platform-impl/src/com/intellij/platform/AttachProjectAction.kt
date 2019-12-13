// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform

import com.intellij.ide.GeneralSettings
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isProjectDirectoryExistsUsingIo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectAttachProcessor
import java.nio.file.Paths

/**
 * This action is enabled when confirmOpenNewProject option is set in settings to either OPEN_PROJECT_NEW_WINDOW or
 * OPEN_PROJECT_SAME_WINDOW, so there is no dialog shown on open directory action, which makes attaching a new project impossible.
 * This action provides a way to do that in this case.
 *
 * @author traff
 */
internal class AttachProjectAction : AnAction("Attach project..."), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ProjectAttachProcessor.canAttachToProject() &&
                                         GeneralSettings.getInstance().confirmOpenNewProject != GeneralSettings.OPEN_PROJECT_ASK
  }

  override fun actionPerformed(e: AnActionEvent) {
    val descriptor = OpenProjectFileChooserDescriptor(true)
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    FileChooser.chooseFiles(descriptor, project, null) {
      attachProject(it[0], project)
    }
  }
}


private fun attachProject(virtualFile: VirtualFile, project: Project) {
  var baseDir: VirtualFile? = virtualFile
  if (!virtualFile.isDirectory) {
    baseDir = virtualFile.parent
    while (baseDir != null) {
      if (isProjectDirectoryExistsUsingIo(baseDir)) {
        break
      }
      baseDir = baseDir.parent
    }
  }

  if (baseDir == null) {
    Messages.showErrorDialog("Project not found in ${virtualFile.path}", "Can't Attach Project")
  }
  else {
    PlatformProjectOpenProcessor.attachToProject(project, Paths.get(FileUtil.toSystemDependentName(baseDir.path)), null)
  }
}