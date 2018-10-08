// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.vcsUtil.VcsImplUtil

private val LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.VcsIgnoreFilesChecker")

class VcsIgnoreFilesChecker : StartupActivity, DumbAware {

  override fun runActivity(project: Project) = generateVcsIgnoreFileIfNeeded(project)

  private fun generateVcsIgnoreFileIfNeeded(project: Project) {
    val basePath = project.basePath
    val projectBaseDir = if (basePath != null) LocalFileSystem.getInstance().findFileByPath(basePath) else null
    val vcs = ProjectLevelVcsManager.getInstance(project).findVersioningVcs(projectBaseDir)
    if (vcs != null) {
      LOG.debug("Generate VCS file for $vcs")
      VcsImplUtil.generateIgnoreFileIfNeeded(project, vcs)
    }
  }
}