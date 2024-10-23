// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task

import com.intellij.execution.CommandLineUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.text.nullize
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.loadFileComparisonTestLoggerInitScript
import org.jetbrains.plugins.gradle.service.execution.loadIjTestLoggerInitScript
import org.jetbrains.plugins.gradle.service.execution.loadJvmOptionsInitScript
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

class JavaGradleTaskManagerExtension : GradleTaskManagerExtension {

  override fun configureTasks(
    projectPath: String,
    id: ExternalSystemTaskId,
    settings: GradleExecutionSettings,
    gradleVersion: GradleVersion?,
  ) {
    configureTestLogger(settings)
    configureJvmOptions(settings)
  }

  private fun configureTestLogger(settings: GradleExecutionSettings) {
    if (settings.isRunAsTest) {
      if (settings.isBuiltInTestEventsUsed) {
        settings.addInitScript(TEST_LOGGER_SCRIPT_NAME, loadFileComparisonTestLoggerInitScript())
      }
      else {
        settings.addInitScript(TEST_LOGGER_SCRIPT_NAME, loadIjTestLoggerInitScript())
      }
    }
  }

  private fun configureJvmOptions(settings: GradleExecutionSettings) {
    val jvmParameters = settings.jvmParameters.nullize() ?: return

    LOG.assertTrue(
      !jvmParameters.contains(ForkedDebuggerHelper.JVM_DEBUG_SETUP_PREFIX),
      "Please use org.jetbrains.plugins.gradle.service.debugger.GradleJvmDebuggerBackend to setup debugger"
    )

    var jvmArgs = ParametersListUtil.parse(jvmParameters)
    if (SystemInfo.isWindows) {
      jvmArgs = jvmArgs.map { CommandLineUtil.escapeParameterOnWindows(it, false) }
    }

    settings.addInitScript(JVM_OPTIONS_SCRIPT_NAME, loadJvmOptionsInitScript(settings.tasks, jvmArgs))
  }

  companion object {
    private const val TEST_LOGGER_SCRIPT_NAME = "ijTestLogger"
    private const val JVM_OPTIONS_SCRIPT_NAME = "ijJvmOptions"

    private val LOG = Logger.getInstance(JavaGradleTaskManagerExtension::class.java)
  }
}
