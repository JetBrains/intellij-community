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
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      project.messageBus
        .connect()
        .subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
          generateVcsIgnoreFileInStoreDirIfNeeded(project)
        })
      StartupManager.getInstance(project).runWhenProjectIsInitialized {
        generateVcsIgnoreFileInRootIfNeeded(project)
      }
    }
  }

  private fun generateVcsIgnoreFileInStoreDirIfNeeded(project: Project) =
    ApplicationManager.getApplication().executeOnPooledThread {
      if (project.isDisposed) return@executeOnPooledThread

      //generate ignore file in .idea directory silently
      if (project.isDirectoryBased) {
        project.stateStore.projectConfigDir?.let { projectConfigDirPath ->
          LocalFileSystem.getInstance().findFileByPath(projectConfigDirPath)?.let { projectConfigDirVFile ->
            VcsUtil.getVcsFor(project, projectConfigDirVFile)?.let { vcs ->
              LOG.debug("Generate VCS ignore file in $projectConfigDirPath for vcs ${vcs.name}")
              VcsImplUtil.generateIgnoreFileIfNeeded(project, vcs, projectConfigDirVFile)
            }
          }
        }
      }
    }

  private fun generateVcsIgnoreFileInRootIfNeeded(project: Project) =
    ApplicationManager.getApplication().executeOnPooledThread {
      if (project.isDisposed) return@executeOnPooledThread

      //propose to manage (generate) ignore file in project VCS root directory
      project.projectFile?.let { projectFile ->
        VcsUtil.getVcsRootFor(project, projectFile)?.let { projectFileVcsRoot ->
          VcsUtil.getVcsFor(project, projectFileVcsRoot)?.let { vcs ->
            LOG.debug("Propose manage VCS ignore in ${projectFileVcsRoot.path} for vcs ${vcs.name}")
            VcsImplUtil.proposeUpdateIgnoreFile(project, vcs, projectFileVcsRoot)
          }
        }
      }
    }
}