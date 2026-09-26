// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.TestRunner

/**
 * The command sets delegatedBuild(Gradle if true and Idea if not) and testRunner[org.jetbrains.plugins.gradle.settings.TestRunner] for all gradle projects
 * Syntax: %setGradleDelegatedBuildCommand [delegatedBuild] [testRunner]
 * Example: %setGradleDelegatedBuildCommand true PLATFORM
 */
class SetGradleDelegatedBuildCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "setGradleDelegatedBuildCommand"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val (delegatedBuild, testRunner) = extractCommandList(prefix, " ")
    GradleSettings.getInstance(project).linkedProjectsSettings.forEach {
      it.delegatedBuild = delegatedBuild.toBoolean()
      it.testRunner = TestRunner.valueOf(testRunner)
    }
  }

  override fun getName(): String {
    return NAME
  }
}