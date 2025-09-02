// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectStorePathManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * This action is enabled when confirmOpenNewProject option is set in settings to either OPEN_PROJECT_NEW_WINDOW or
 * OPEN_PROJECT_SAME_WINDOW, so there is no dialog shown on open directory action, which makes attaching a new project impossible.
 * This action provides a way to do that in this case.
 */
open class AttachProjectAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ProjectAttachProcessor.canAttachToProject() &&
                                         GeneralSettings.getInstance().confirmOpenNewProject != GeneralSettings.OPEN_PROJECT_ASK
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    chooseAndAttachToProject(project)
  }

  open fun validateDirectory(project: Project, directory: VirtualFile): Boolean {
    return true
  }

  fun chooseAndAttachToProject(project: Project) {
    chooseAndAttachToProject(project, ::validateDirectory)
  }

  companion object {
    @ApiStatus.Internal
    fun chooseAndAttachToProject(project: Project, validateDirectory: (Project, VirtualFile) -> Boolean) {
      val descriptor = OpenProjectFileChooserDescriptor(true)
      var preselectedDirectory = project.getUserData(TO_SELECT_KEY)?.let {
        project.putUserData(TO_SELECT_KEY, null) // reset the value
        LocalFileSystem.getInstance().findFileByNioFile(it)
      }

      if (preselectedDirectory == null) {
        val defaultProjectDirectory = GeneralLocalSettings.getInstance().defaultProjectDirectory
        preselectedDirectory = if (defaultProjectDirectory.isEmpty()) {
          VfsUtil.findFileByIoFile(File(SystemProperties.getUserHome()), true)
        }
        else {
          VfsUtil.findFileByIoFile(File(defaultProjectDirectory), true)
        }
      }

      FileChooser.chooseFiles(descriptor, project, preselectedDirectory) {
        val directory = it[0]
        if (validateDirectory(project, directory)) {
          attachProject(directory, project)
        }
      }
    }

    @JvmStatic
    val TO_SELECT_KEY: Key<Path> = Key.create("attach_to_select_key")

    @RequiresEdt
    fun attachProject(virtualFile: VirtualFile, project: Project) {
      var baseDir: VirtualFile? = virtualFile
      if (!virtualFile.isDirectory) {
        baseDir = virtualFile.parent
        while (baseDir != null) {
          val isProjectDirectory = try {
            service<ProjectStorePathManager>().testStoreDirectoryExistsForProjectRoot(baseDir.toNioPath())
          }
          catch (e: InvalidPathException) {
            false
          }
          if (isProjectDirectory) {
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
        runWithModalProgressBlocking(project, ActionsBundle.message("action.AttachProject.text")) {
          attachToProjectAsync(projectToClose = project, projectDir = Path.of(FileUtil.toSystemDependentName(baseDir.path)))
        }
      }
    }
  }
}
