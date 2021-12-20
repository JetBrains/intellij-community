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
    import org.gradle.api.tasks.testing.Test
    import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
    
    def getCurrentProject() {
        def currentPath = gradle.startParameter.currentDir.path
        return rootProject.allprojects.find { it.projectDir.path == currentPath }
    }
    

    static def removePrefix(def string, def prefix) {
        if (string.startsWith(prefix)) {
            return string.minus(prefix)
        }
        return null
    }
    
    static def getRelativeTaskPath(def project, def task) {
        def taskPath = task.path
        def projectPath = project?.path
        if (projectPath == null) return null
        def path = removePrefix(taskPath, projectPath)
        if (path == null) return null
        return removePrefix(path, ":") ?: path
    }
    
    static def getPossibleTaskNames(def project, def task) {
        def relativeTaskPath = getRelativeTaskPath(project, task)
        if (relativeTaskPath == null) {
            [task.path]
        } else {
            [task.name, task.path, relativeTaskPath]
        }
    }
    
    static def isMatchedTask(def project, def task, def matchers) {
        def possibleNames = getPossibleTaskNames(project, task)
        if (matchers.any { it in possibleNames }) {
            return "MATCHED"
        }
        if (possibleNames.any { matchers.any { matcher -> it.startsWith(matcher) } }) {
            return "PARTIALLY MATCHED"
        }
        return "NOT MATCHED"
    }
    
    def filterStartTasks(def tasks) {
        def currentProject = getCurrentProject()
        logger.debug("Current Project: ${'$'}{currentProject}")
    
        def startTaskNames = gradle.startParameter.taskNames
        if (startTaskNames.isEmpty()) {
            startTaskNames = (currentProject ?: project).defaultTasks
        }
        logger.debug("Start Tasks Names: ${'$'}{startTaskNames}")
    
        def tasksMatchStatus = tasks.collect { [it, isMatchedTask(currentProject, it, startTaskNames)] }
        def matchedTasks = tasksMatchStatus.findAll { it[1] == "MATCHED" }.collect { it[0] }
        if (matchedTasks.isEmpty()) {
            matchedTasks = tasksMatchStatus.findAll { it[1] == "PARTIALLY MATCHED" }.collect { it[0] }
        }
        logger.debug("Matched tasks: ${'$'}{matchedTasks}")
        return matchedTasks
    }
    
    gradle.taskGraph.whenReady { taskGraph ->
        //noinspection GroovyAssignabilityCheck
        def debugAllIsEnabled = Boolean.valueOf(System.properties["idea.gradle.debug.all"])
        logger.debug("idea.gradle.debug.all is ${'$'}{debugAllIsEnabled}")

        taskGraph.allTasks.each { Task task ->
            if (task instanceof Test) {
                task.maxParallelForks = 1
                task.forkEvery = 0
            }
        }
        def jvmTasks = taskGraph.allTasks.findAll { task -> task instanceof JavaForkOptions }
        def matchedTasks = debugAllIsEnabled ? jvmTasks : filterStartTasks(jvmTasks)
        matchedTasks.each { task ->
            def moduleDir = task.project.projectDir.path
            task.doFirst {
                def debugPort = ForkedDebuggerHelper.setupDebugger('${id()}', task.path, '$parameters', moduleDir)
                def jvmArgs = task.jvmArgs.findAll { !it?.startsWith('-agentlib:jdwp') && !it?.startsWith('-Xrunjdwp') }
                jvmArgs << ForkedDebuggerHelper.JVM_DEBUG_SETUP_PREFIX + ForkedDebuggerHelper.addrFromProperty + ':' + debugPort
                task.jvmArgs = jvmArgs
            }
            task.doLast {
                ForkedDebuggerHelper.signalizeFinish('${id()}', task.path)
            }
        }
    }
    """.trimIndent().split("\n")
}