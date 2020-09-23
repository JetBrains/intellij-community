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
class JBProtocolProjectLocator(locator: String) {
  private val aliases = locator.split(",").map { it.toLowerCase() }
  fun matches(projectName: String): Boolean = projectName.toLowerCase() in aliases
}

@ApiStatus.Internal
fun openProjectAndExecute(projectLocator: JBProtocolProjectLocator, callback: (Project) -> Unit) {
  for (recentProjectAction in RecentProjectListActionProvider.getInstance().getActions()) {
    if (recentProjectAction !is ReopenProjectAction || !projectLocator.matches(recentProjectAction.projectName)) {
      continue
    }

    for (project in ProjectUtil.getOpenProjects()) {
      if (projectLocator.matches(project.name)) {
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