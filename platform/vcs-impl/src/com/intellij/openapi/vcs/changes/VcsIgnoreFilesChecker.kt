// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil

private val LOG = Logger.getInstance(VcsIgnoreFilesChecker::class.java)

class VcsIgnoreFilesChecker(private val project: Project) : ProjectComponent {

  override fun projectOpened() {
    if (project.isDirectoryBased && !ApplicationManager.getApplication().isUnitTestMode) {
      project.messageBus
        .connect()
        .subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
          generateVcsIgnoreFileIfNeeded(project)
        })
    }
  }

  private fun generateVcsIgnoreFileIfNeeded(project: Project) =
    ApplicationManager.getApplication().executeOnPooledThread {
      if (project.isDisposed) return@executeOnPooledThread

      val projectConfigDirPath = project.stateStore.projectConfigDir ?: return@executeOnPooledThread
      val projectConfigDirVFile = LocalFileSystem.getInstance().findFileByPath(projectConfigDirPath) ?: return@executeOnPooledThread

      val vcs = VcsUtil.getVcsFor(project, projectConfigDirVFile) ?: return@executeOnPooledThread

      LOG.debug("Generate VCS ignore file for " + vcs.name)
      VcsImplUtil.generateIgnoreFileIfNeeded(project, vcs, projectConfigDirVFile)
    }
}