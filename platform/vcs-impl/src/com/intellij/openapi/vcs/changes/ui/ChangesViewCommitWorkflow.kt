// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener

class ChangesViewCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  init {
    val connection = project.messageBus.connect()
    connection.subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      Disposer.dispose(connection)

      runInEdt { updateVcses(vcsManager.allActiveVcss.toSet()) }
    })
  }
}