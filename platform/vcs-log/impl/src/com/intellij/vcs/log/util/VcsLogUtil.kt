// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog

fun runWhenVcsAndLogIsReady(project: Project, action: (VcsLogManager) -> Unit) {
  val logManager = VcsProjectLog.getInstance(project).logManager
  if (logManager == null) {
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      runInEdt { VcsProjectLog.runWhenLogIsReady(project) { manager -> action(manager) } }
    }
  }
  else {
    action(logManager)
  }
}