// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE
import com.intellij.vcs.log.util.VcsLogUtil

internal class VcsProjectLogErrorHandler(private val project: Project, private val projectLog: VcsProjectLog) {
  private var recreatedCount = 0

  @RequiresEdt
  fun recreateOnError(t: Throwable) {
    if (projectLog.isDisposing) return

    recreatedCount++
    val logMessage = "Recreating Vcs Log after storage corruption. Recreated count $recreatedCount"
    if (recreatedCount % RECREATE_LOG_TRIES == 0) {
      thisLogger().error(logMessage, t)
      val manager = projectLog.logManager
      if (manager != null && manager.isLogVisible) {
        val balloonMessage = VcsLogBundle.message("vcs.log.recreated.due.to.corruption",
                                                  VcsLogUtil.getVcsDisplayName(project, manager),
                                                  recreatedCount,
                                                  LOG_CACHE,
                                                  ApplicationNamesInfo.getInstance().fullProductName)
        VcsBalloonProblemNotifier.showOverChangesView(project, balloonMessage, MessageType.ERROR)
      }
    }
    else {
      thisLogger().debug(logMessage, t)
    }

    projectLog.disposeLog(true)
  }

  companion object {
    private const val RECREATE_LOG_TRIES = 5
  }
}