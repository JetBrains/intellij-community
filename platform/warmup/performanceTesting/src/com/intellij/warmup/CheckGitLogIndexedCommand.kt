// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.vcs.log.data.EmptyIndex
import com.intellij.vcs.log.impl.VcsProjectLog
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

/**
 * Checks that VCS log is fully indexed on project closing
 */
class CheckGitLogIndexedCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<in Any> {
    val callback = assertVcsLogIndexed()

    return callback.toPromise()
  }

  private fun assertVcsLogIndexed(): ActionCallbackProfilerStopper {
    val callback = ActionCallbackProfilerStopper()

    ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        @Suppress("RetrievingService") val projectLog = project.serviceIfCreated<VcsProjectLog>()
        if (projectLog == null) {
          callback.reject("Vcs log was not initialized")
          return
        }
        val projectLogManager = projectLog.logManager!!
        ApplicationManager.getApplication().invokeAndWait {
          if (!projectLogManager.isLogUpToDate) {
            callback.reject("Vcs log is not up to date")
          }
        }
        val index = projectLogManager.dataManager.index
        if (index is EmptyIndex) {
          callback.reject("Empty VCS index; nothing to test")
        }
        else {
          for (root in index.indexingRoots) {
            if (index.isIndexingEnabled(root) && !index.isIndexed(root)) {
              callback.reject("Vcs root ${root.path} does not contain complete git log")
              return
            }
          }
          callback.setDone()
        }
      }
    })
    return callback
  }

}