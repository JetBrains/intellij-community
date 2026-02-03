// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware.Companion.TOPIC
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware.Listener
import com.intellij.openapi.externalSystem.autoimport.ProjectRefreshAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.toPromise

/**
 * The command refreshes project (like click 'Load Gradle changes')
 * Syntax: %refreshGradleProject
 */
class RefreshGradleProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "refreshGradleProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val myProject = context.project
    val messageBus = ApplicationManager.getApplication().messageBus
    val callback = ActionCallback()
    messageBus.connect().subscribe(TOPIC, object : Listener {
      override fun onNotificationChanged(project: Project) {
        if (myProject === project) {
          callback.setDone()
        }
      }
    })
    ProjectRefreshAction.Manager.refreshProject(myProject)
    callback.toPromise().await()
  }

  override fun getName(): String {
    return NAME
  }
}