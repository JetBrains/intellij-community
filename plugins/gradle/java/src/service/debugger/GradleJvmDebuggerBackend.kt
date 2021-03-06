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
    
    def getCurrentProject() {
      def currentPath = gradle.startParameter.currentDir.path
      def currentProject = rootProject.allprojects
        .find { it.projectDir.path == currentPath }
      return currentProject == null ? project : currentProject
    }
    
    def findAllTaskPathsInStartParameters() {
      def currentProject = getCurrentProject()
      logger.debug("Current project: ${'$'}{currentProject}")
      def startTaskNames = gradle.startParameter.taskNames
      if (startTaskNames.isEmpty()) {
        startTaskNames = currentProject.defaultTasks
      }
      logger.debug("Start Tasks Names: ${'$'}{startTaskNames}")
      def allTaskContainers = currentProject.allprojects.collect { it.tasks }
      return startTaskNames.findResults { taskName -> 
        allTaskContainers.findResult { it.findByPath(taskName)?.path }
          ?: allTaskContainers.findResult { it.find { it.name.startsWith(taskName) || it.path.startsWith(taskName) }?.path }
      }
    }
    
    gradle.taskGraph.whenReady { taskGraph ->
      def debugAllIsEnabled = Boolean.valueOf(System.properties["idea.gradle.debug.all"])
      def taskPathsList = debugAllIsEnabled ? [] : findAllTaskPathsInStartParameters()
      logger.debug("idea.gradle.debug.all is ${'$'}{debugAllIsEnabled}")
      logger.debug("Task paths: ${'$'}{taskPathsList}")
      
      taskGraph.allTasks.each { Task task ->
        if (task instanceof org.gradle.api.tasks.testing.Test) {
          task.maxParallelForks = 1
          task.forkEvery = 0
        }
        if (task instanceof JavaForkOptions && (debugAllIsEnabled || taskPathsList.contains(task.path))) {
          task.doFirst {
            def moduleDir = task.project.projectDir.path
            def debugPort = ForkedDebuggerHelper.setupDebugger('${id()}', task.path, '$parameters', moduleDir)
            def jvmArgs = task.jvmArgs.findAll{!it?.startsWith('-agentlib:jdwp') && !it?.startsWith('-Xrunjdwp')}
            jvmArgs << ForkedDebuggerHelper.JVM_DEBUG_SETUP_PREFIX + ForkedDebuggerHelper.addrFromProperty +':' + debugPort
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