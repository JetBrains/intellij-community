// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
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
    if (ApplicationManager.getApplication().isUnitTestMode) return

    project.messageBus
      .connect()
      .subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
        StartupManager.getInstance(project).runWhenProjectIsInitialized {
          ApplicationManager.getApplication().executeOnPooledThread {
            generateVcsIgnoreFileInStoreDirIfNeeded(project)
            generateVcsIgnoreFileInRootIfNeeded(project)
          }
        }
      })
  }

  /**
   * Generate ignore file in .idea directory silently
   */
  private fun generateVcsIgnoreFileInStoreDirIfNeeded(project: Project) {
    if (project.isDisposed || !project.isDirectoryBased) return

    val projectConfigDirPath = project.stateStore.projectConfigDir ?: return
    val projectConfigDirVFile = LocalFileSystem.getInstance().findFileByPath(projectConfigDirPath) ?: return
    val vcs = VcsUtil.getVcsFor(project, projectConfigDirVFile) ?: return

    LOG.debug("Generate VCS ignore file in $projectConfigDirPath for vcs ${vcs.name}")
    VcsImplUtil.generateIgnoreFileIfNeeded(project, vcs, projectConfigDirVFile)
  }

  /**
   * Propose to manage (generate) ignore file in project VCS root directory.
   */
  private fun generateVcsIgnoreFileInRootIfNeeded(project: Project) {
    if (project.isDisposed) return

    val projectFile = project.getProjectConfigDirOrProjectFile() ?: return
    val projectFileVcsRoot = VcsUtil.getVcsRootFor(project, projectFile) ?: return
    val vcs = VcsUtil.getVcsFor(project, projectFileVcsRoot) ?: return

    LOG.debug("Propose manage VCS ignore in ${projectFileVcsRoot.path} for vcs ${vcs.name}")
    VcsImplUtil.proposeUpdateIgnoreFile(project, vcs, projectFileVcsRoot)
  }

  private fun Project.getProjectConfigDirOrProjectFile() =
    if (isDirectoryBased) project.stateStore.projectConfigDir?.let(LocalFileSystem.getInstance()::findFileByPath)
    else project.projectFile
}