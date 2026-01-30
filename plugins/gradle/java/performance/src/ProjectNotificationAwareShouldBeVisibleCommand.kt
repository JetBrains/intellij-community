// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter

/**
 * The command validates if Project notification aware(Maven/Gradle icon when you need to reload project) is [visible]
 * Syntax: %projectNotificationAwareShouldBeVisible [visible]
 * Example: %projectNotificationAwareShouldBeVisible true
 */
class ProjectNotificationAwareShouldBeVisibleCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "projectNotificationAwareShouldBeVisible"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val shouldBeVisible = extractCommandArgument(PREFIX).toBoolean()
    val actual = ExternalSystemProjectNotificationAware.getInstance(project).isNotificationVisible()
    if (actual != shouldBeVisible)
      throw IllegalStateException("Project notification aware should be visible. Expected: $shouldBeVisible Actual: $actual")
  }

  override fun getName(): String {
    return NAME
  }
}