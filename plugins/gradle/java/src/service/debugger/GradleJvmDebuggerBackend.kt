// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.debugger

import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension
import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension.RUNTIME_MODULE_DIR_KEY
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import com.intellij.openapi.project.Project

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

  override fun initializationCode(dispatchPort: String, parameters: String) =
    //language=Gradle
    """
    import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper    
    gradle.taskGraph.whenReady { taskGraph ->
      def debugAllIsEnabled = Boolean.valueOf(System.properties["ij.gradle.debug.all"])
      def taskPathsList = []
      if (!debugAllIsEnabled) {
        def currentPath = gradle.getStartParameter().getCurrentDir().path
        def currentProject = rootProject.allprojects.find { it.projectDir.path == currentPath }
        if (currentProject == null) {
          currentProject = project
        }
        
        def startTaskNames = gradle.getStartParameter().getTaskNames()
        if (startTaskNames.isEmpty()) {
          startTaskNames = currentProject.getDefaultTasks() 
        } 
        taskPathsList = startTaskNames.collect { currentProject.tasks.findByPath(it)?.path }.findAll { it != null }
      }
      
      taskGraph.allTasks.each { Task task ->
        if (task instanceof org.gradle.api.tasks.testing.Test) {
          task.maxParallelForks = 1
          task.forkEvery = 0
        }
        task.path
        if (task instanceof JavaForkOptions && (debugAllIsEnabled || taskPathsList.contains(task.path))) {
          task.doFirst {
            def moduleDir = task.project.projectDir.path
            def debugPort = ForkedDebuggerHelper.setupDebugger('${id()}', task.path, '$parameters', moduleDir)
            def jvmArgs = task.jvmArgs.findAll{!it?.startsWith('-agentlib:jdwp') && !it?.startsWith('-Xrunjdwp')}
            jvmArgs << ForkedDebuggerHelper.JVM_DEBUG_SETUP_PREFIX + debugPort
            task.jvmArgs = jvmArgs
          }
          task.doLast {
              ForkedDebuggerHelper.signalizeFinish('${id()}', task.path)
          }
        }
      }
    }
    """.trimIndent().split("\n")
}