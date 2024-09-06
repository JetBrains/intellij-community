// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.debugger

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension
import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension.RUNTIME_MODULE_DIR_KEY
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.execution.loadJvmDebugInitScript

class GradleJvmDebuggerBackend : DebuggerBackendExtension {
  override fun id() = "Gradle JVM"

  override fun debugConfigurationSettings(project: Project,
                                          processName: String,
                                          processParameters: String): RunnerAndConfigurationSettings {
    val runSettings = getInstance(project).createConfiguration(processName, RemoteConfigurationType::class.java)
    val description = splitParameters(processParameters)

    val configuration = runSettings.configuration as RemoteConfiguration
    configuration.HOST = "localhost"
    configuration.PORT = description[ForkedDebuggerHelper.DEBUG_SERVER_PORT_KEY]
    configuration.USE_SOCKET_TRANSPORT = true
    configuration.SERVER_MODE = true
    configuration.putUserData(RUNTIME_MODULE_DIR_KEY, description[ForkedDebuggerHelper.RUNTIME_MODULE_DIR_KEY])
    return runSettings
  }

  override fun initializationCode(project: Project?, dispatchPort: String?, parameters: String): List<String> {
    val javaParameters = JavaParameters()
    RemoteConnectionBuilder.addDebuggerAgent(javaParameters, project, false)
    val jvmArgs = javaParameters.vmParametersList.list.filterNot { it.startsWith("-agentlib:jdwp=") }
    val initScript = loadJvmDebugInitScript(id(), parameters, jvmArgs)
    return initScript.split("\n")
  }
}