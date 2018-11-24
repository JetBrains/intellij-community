/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
class AttachProjectAction : AnAction("Attach project..."), DumbAware {
  override fun update(e: AnActionEvent?) {
    e?.presentation?.isEnabledAndVisible = ProjectAttachProcessor.canAttachToProject() &&
                                           GeneralSettings.getInstance().confirmOpenNewProject != GeneralSettings.OPEN_PROJECT_ASK
  }

  override fun actionPerformed(e: AnActionEvent?) {
    val descriptor = OpenProjectFileChooserDescriptor(true)
    val project = e?.getData(CommonDataKeys.PROJECT)


    FileChooser.chooseFiles(descriptor, project, null) { files ->
      attachProject(files[0], project)
    }
  }
}


fun attachProject(virtualFile: VirtualFile, project: Project?) {
  var baseDir: VirtualFile? = virtualFile

  if (!baseDir!!.isDirectory) {
    baseDir = virtualFile.parent
    while (baseDir != null) {
      if (isProjectDirectoryExistsUsingIo(baseDir)) {
        break
      }
      baseDir = baseDir.parent
    }
  }

  if (baseDir != null) {
    PlatformProjectOpenProcessor.attachToProject(project, Paths.get(FileUtil.toSystemDependentName(baseDir.path)), null)
  } else {
    Messages.showErrorDialog("Project not found in ${virtualFile.path}", "Can't Attach Project")
  }
}