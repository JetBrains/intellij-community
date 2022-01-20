// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isProjectDirectoryExistsUsingIo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectAttachProcessor
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This action is enabled when confirmOpenNewProject option is set in settings to either OPEN_PROJECT_NEW_WINDOW or
 * OPEN_PROJECT_SAME_WINDOW, so there is no dialog shown on open directory action, which makes attaching a new project impossible.
 * This action provides a way to do that in this case.
 *
 * @author traff
 */
open class AttachProjectAction : AnAction(ActionsBundle.message("action.AttachProject.text")), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ProjectAttachProcessor.canAttachToProject() &&
                                         GeneralSettings.getInstance().confirmOpenNewProject != GeneralSettings.OPEN_PROJECT_ASK
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    chooseAndAttachToProject(project)
  }

  open fun validateDirectory(project: Project, directory: VirtualFile): Boolean {
    return true
  }

  fun chooseAndAttachToProject(project: Project) {
    val descriptor = OpenProjectFileChooserDescriptor(true)
    var preselectedDirectory = project.getUserData(TO_SELECT_KEY)?.let {
      project.putUserData(TO_SELECT_KEY, null) // reset the value
      LocalFileSystem.getInstance().findFileByNioFile(it)
    }
    if (preselectedDirectory == null && StringUtil.isNotEmpty(GeneralSettings.getInstance().defaultProjectDirectory)) {
      preselectedDirectory = VfsUtil.findFileByIoFile(File(GeneralSettings.getInstance().defaultProjectDirectory), true)
    }

    FileChooser.chooseFiles(descriptor, project, preselectedDirectory) {
      val directory = it[0]
      if (validateDirectory(project, directory)) {
        attachProject(directory, project)
      }
    }
  }

  companion object {
    @JvmStatic
    val TO_SELECT_KEY = Key.create<Path>("attach_to_select_key")

    fun attachProject(virtualFile: VirtualFile, project: Project) {
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
        Messages.showErrorDialog(IdeBundle.message("dialog.message.attach.project.not.found", virtualFile.path),
                                 IdeBundle.message("dialog.title.attach.project.error"))
      }
      else {
        PlatformProjectOpenProcessor.attachToProject(project, Paths.get(FileUtil.toSystemDependentName(baseDir.path)), null)
      }
    }
  }
}
