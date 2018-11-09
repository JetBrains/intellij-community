// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil

class VcsIgnoreFilesChecker(private val project: Project) : ProjectComponent {

  override fun projectOpened() =
    project.messageBus
      .connect()
      .subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
        generateVcsIgnoreFileIfNeeded(project)
      })

  private fun generateVcsIgnoreFileIfNeeded(project: Project) =
    ApplicationManager.getApplication().executeOnPooledThread {
      if (!project.isDisposed) {
        val projectFile = project.projectFile ?: return@executeOnPooledThread

        val projectVcsRoot = VcsUtil.getVcsRootFor(project, projectFile)
        if (projectVcsRoot != null) {
          VcsImplUtil.generateIgnoreFileIfNeeded(project, projectVcsRoot)
        }
      }
    }
}