// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Paths

@ApiStatus.Internal
fun openProjectAndExecute(projectName: String, callback: (Project) -> Unit) {
  for (recentProjectAction in RecentProjectListActionProvider.getInstance().getActions()) {
    if (recentProjectAction !is ReopenProjectAction || recentProjectAction.projectName.toLowerCase() != projectName) {
      continue
    }

    for (project in ProjectUtil.getOpenProjects()) {
      if (project.name == projectName) {
        callback(project)
        return
      }
    }

    ApplicationManager.getApplication().invokeLater(Runnable {
      val project = RecentProjectsManagerBase.instanceEx.openProject(Paths.get(recentProjectAction.projectPath), OpenProjectTask())
                    ?: return@Runnable
      StartupManager.getInstance(project).runAfterOpened {
        DumbService.getInstance(project).runWhenSmart {
          callback(project)
        }
      }
    }, ModalityState.NON_MODAL)
  }
}