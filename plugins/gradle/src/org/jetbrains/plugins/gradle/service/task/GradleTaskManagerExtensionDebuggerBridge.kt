// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task

import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.platform.externalSystem.rt.ExternalSystemRtClass
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.joinInitScripts
import org.jetbrains.plugins.gradle.service.execution.loadCommonDebuggerUtilsScript
import org.jetbrains.plugins.gradle.service.execution.loadCommonTasksUtilsScript
import org.jetbrains.plugins.gradle.service.execution.loadToolingExtensionProvidingInitScript
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

@ApiStatus.Internal
@Suppress("DEPRECATION")
class GradleTaskManagerExtensionDebuggerBridge : GradleTaskManagerExtension {

  override fun configureTasks(
    projectPath: String,
    id: ExternalSystemTaskId,
    settings: GradleExecutionSettings,
    gradleVersion: GradleVersion?,
  ) {
    val project = id.findProject()

    val toolingInitScripts = listOf(
      loadToolingExtensionProvidingInitScript(
        ExternalSystemRtClass::class.java
      ),
      loadCommonTasksUtilsScript(),
      loadCommonDebuggerUtilsScript()
    )

    val dispatchPort = settings.getUserData(ExternalSystemRunnableState.DEBUGGER_DISPATCH_PORT_KEY)?.toString()
    if (dispatchPort != null) {
      settings.addEnvironmentVariable(DEBUGGER_ENABLED, "true")
    }

    val debugOptions = settings.getUserData(ExternalSystemRunnableState.DEBUGGER_PARAMETERS_KEY) ?: ""

    DebuggerBackendExtension.EP_NAME.forEachExtensionSafe { extension ->
      if (extension.isAlwaysAttached || dispatchPort != null) {
        val initScripts = extension.initializationCode(project, dispatchPort, debugOptions)
        if (!initScripts.isEmpty()) {
          val initScript = joinInitScripts(toolingInitScripts + initScripts)
          val extensionClassName = extension.javaClass.name
          settings.addInitScript(DEBUGGER_SCRIPT_PREFIX + extensionClassName, initScript)
        }
      }
    }

    DebuggerBackendExtension.EP_NAME.forEachExtensionSafe { extension ->
      if (extension.isAlwaysAttached) {
        settings.withEnvironmentVariables(
          extension.executionEnvironmentVariables(project, dispatchPort, debugOptions)
        )
      }
    }
  }

  companion object {
    private const val DEBUGGER_SCRIPT_PREFIX = "ijDebugger-"

    // debug flag that will always be passed at runtime if debugging is enabled
    const val DEBUGGER_ENABLED: String = "DEBUGGER_ENABLED"
  }
}
