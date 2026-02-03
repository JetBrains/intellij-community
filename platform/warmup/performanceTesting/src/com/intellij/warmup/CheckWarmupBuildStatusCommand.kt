// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

internal class CheckWarmupBuildStatusCommand(text: String, line: Int) : AbstractCommand(text, line) {

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val callback = ActionCallbackProfilerStopper()

    val messageBus = ApplicationManager.getApplication().messageBus
    val connection = messageBus.simpleConnect()
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        when (val buildStatus = WarmupBuildStatus.currentStatus()) {
          is WarmupBuildStatus.NotInvoked -> callback.reject("Warm-up build was not invoked")
          is WarmupBuildStatus.Failure -> callback.reject("Warm-up build failed: ${buildStatus.message}")
          else -> callback.setDone()
        }
      }
    })

    return callback.toPromise()
  }
}