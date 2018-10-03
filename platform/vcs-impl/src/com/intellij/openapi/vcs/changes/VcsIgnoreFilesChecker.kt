// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupActivity
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil

private val LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.VcsIgnoreFilesChecker")

class VcsIgnoreFilesChecker : StartupActivity, DumbAware {

  override fun runActivity(project: Project) = generateVcsIgnoreFileIfNeeded(project)

  private fun generateVcsIgnoreFileIfNeeded(project: Project) {
    //at the moment we check and generate if needed ignore file only for projectDir. In future we can utilize VcsRootDetector for that purpose
    val projectDir = project.guessProjectDir() ?: return
    val vcs = VcsUtil.getVcsFor(project, projectDir)
    if (vcs != null) {
      LOG.debug("Generate VCS file for $vcs")
      VcsImplUtil.generateIgnoreFileIfNeeded(project, vcs, projectDir)
    }
  }
}