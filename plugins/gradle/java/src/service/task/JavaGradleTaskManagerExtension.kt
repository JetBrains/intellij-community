// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.execution.CommandLineUtil
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.text.nullize
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.debugger.GradleJvmDebuggerBackend
import org.jetbrains.plugins.gradle.service.execution.loadFileComparisonTestLoggerInitScript
import org.jetbrains.plugins.gradle.service.execution.loadIjTestLoggerInitScript
import org.jetbrains.plugins.gradle.service.execution.loadJvmDebugInitScript
import org.jetbrains.plugins.gradle.service.execution.loadJvmOptionsInitScript
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import kotlin.text.startsWith

class JavaGradleTaskManagerExtension : GradleTaskManagerExtension {

  override fun configureTasks(
    projectPath: String,
    id: ExternalSystemTaskId,
    settings: GradleExecutionSettings,
    gradleVersion: GradleVersion?,
  ) {
    configureTestLogger(settings)
    configureJvmDebugger(id, settings)
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

  private fun configureJvmDebugger(
    id: ExternalSystemTaskId,
    settings: GradleExecutionSettings,
  ) {
    val debuggerDispatchPort = settings.getUserData(ExternalSystemRunnableState.DEBUGGER_DISPATCH_PORT_KEY)
    val debuggerParameters = settings.getUserData(ExternalSystemRunnableState.DEBUGGER_PARAMETERS_KEY) ?: ""

    settings.addInitScript(JVM_DEBUGGER_SCRIPT_NAME, loadJvmDebugInitScript())

    // no debugging required, as a result, no need to provide any environment
    if (debuggerDispatchPort != null) {

      val javaParameters = JavaParameters()
      RemoteConnectionBuilder.addDebuggerAgent(javaParameters, id.findProject(), false)

      val jvmArgs = javaParameters.vmParametersList.list.filterNot { it.startsWith("-agentlib:jdwp=") }

      settings.withEnvironmentVariables(mapOf(
        "DEBUGGER_ID" to GradleJvmDebuggerBackend.ID,
        "PROCESS_PARAMETERS" to debuggerParameters,
        "PROCESS_OPTIONS" to jvmArgs.joinToString(", ")
      ))
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
    private const val JVM_DEBUGGER_SCRIPT_NAME = "ijJvmDebugger"
    private const val JVM_OPTIONS_SCRIPT_NAME = "ijJvmOptions"

    private val LOG = Logger.getInstance(JavaGradleTaskManagerExtension::class.java)
  }
}
