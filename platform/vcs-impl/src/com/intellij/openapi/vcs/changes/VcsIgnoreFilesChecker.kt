// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil

private val LOG = Logger.getInstance(VcsIgnoreFilesChecker::class.java)

class VcsIgnoreFilesChecker : ProjectManagerListener {

  override fun projectOpened(project: Project) {
    project.messageBus.connect()
      .subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
        ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
          BackgroundTaskUtil.executeOnPooledThread(project, Runnable {
            generateVcsIgnoreFileInRootIfNeeded(project)
          })
        }
      })
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
    if (isDirectoryBased) stateStore.directoryStorePath?.let(LocalFileSystem.getInstance()::findFileByNioFile)
    else projectFile
}