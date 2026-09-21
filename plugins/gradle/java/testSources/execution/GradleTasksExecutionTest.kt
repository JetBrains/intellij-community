// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.process.ProcessOutputType
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.ExternalEventLogSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.runTask
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import junit.framework.AssertionFailedError
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.statistics.GradleTaskExecutionCollector
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import org.junit.runners.Parameterized
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class GradleTasksExecutionTest : GradleTasksExecutionTestCase() {

  @Test
  fun `test fus contains only well known task names`() {
    ExtensionTestUtil.maskExtensions(
      ExternalEventLogSettings.EP_NAME,
      listOf(object : ExternalEventLogSettings {
        override fun forceLoggingAlwaysEnabled(): Boolean = true
        override fun getExtraLogUploadHeaders(): Map<String, String> = emptyMap()
      }),
      testRootDisposable)
    val buildScript = createBuildScriptBuilder()
      .withTask("userDefinedTask") {
        code("dependsOn { subprojects.collect { \"\$it.name:clean\"} }\n  dependsOn { subprojects.collect { \"\$it.name:build\"} }")
      }
      .allprojects { withJavaPlugin() }
      .project(":projectA", Consumer<TestGradleBuildScriptBuilder> { it: TestGradleBuildScriptBuilder -> it.withIdeaPlugin() })
      .project(":projectB", Consumer<TestGradleBuildScriptBuilder> { it: TestGradleBuildScriptBuilder ->
        it.withTask("customTaskFromProjectB")
        it.withPostfix { code("tasks.findByName('build').dependsOn('customTaskFromProjectB')") }
      })
      .project(":projectC", Consumer<TestGradleBuildScriptBuilder> { it: TestGradleBuildScriptBuilder ->
        it.withTask("customTaskFromProjectC")
        it.withPostfix { code("tasks.findByName('clean').dependsOn('customTaskFromProjectC')") }
      })
      .generate()
    createProjectSubFile("build.gradle", buildScript)
    createProjectSubDirs("projectA", "projectB", "projectC")
    createSettingsFile("include 'projectA', 'projectB', 'projectC'")
    val expectedGradleTasks = listOf("compileJava", "processResources", "classes", "jar", "assemble", "compileTestJava",
                                     "processTestResources", "testClasses", "test", "check", "build", "clean", "other")
    val events: List<LogEvent> = collectGradlePerformanceEvents(expectedGradleTasks.size) {
      assertThat(runTaskAndGetErrorOutput(projectPath, "userDefinedTask")).isEmpty()
    }
    val actualGradleTasks = events.filter { it.event.id == "task.executed" }.map { it.event.data["name"].toString() }
    assertCollection(actualGradleTasks, expectedGradleTasks)
  }

  @Test
  fun `run task from subproject`() {
    createProjectSubFile("settings.gradle", "include('m1:m2:m3')")
    createProjectSubFile("buildSrc/settings.gradle", "rootProject.name='my-conventions'")
    createProjectSubFile("m1/m2/m3/build.gradle", """
tasks.register("hello-module") {
    doLast {
        logger.lifecycle("expected!")
    }
}
    """.trimIndent())
    importProject()

    val taskData: TaskData = ExternalSystemApiUtil
      .findProjectTasks(myProject, GradleConstants.SYSTEM_ID, "$projectPath/m1/m2/m3")
      .find { it.name.contains("hello-module") } ?: throw AssertionFailedError("Task 'hello-module' not found")

    val output = runTask(taskData)
    assertThat(output).contains("expected!");
  }

  @Test
  fun `run task from misconfigured subproject with explicit script parameter`() {
    val properSettingsFilePaths = createProjectSubFile("settings.gradle", """
      rootProject.name = "rootProject"
      include('projectA')
      include('projectB')
    """.trimIndent()).canonicalPath
    createProjectSubFile("projectB/build.gradle", """
    """.trimIndent())
    createProjectSubFile("projectA/build.gradle", """
      plugins {
        id 'java-library'
      }
      
      dependencies {
        implementation(project(':projectB'))
      }
      
      tasks.register("hello") {
          doLast {
              configurations.implementation.allArtifacts
              logger.lifecycle("expected!")
          }
      }
    """.trimIndent())
     //--- following settings.gradle files are not expected in regular gradle projects
     //but may appear e.g. when IDEA "new module" wizard creates a spring subproject using vendor's API
    createProjectSubFile("projectA/settings.gradle", """
      rootProject.name = "projectA"
    """.trimIndent())
    createProjectSubFile("projectB/settings.gradle", """
      rootProject.name = "projectB"
    """.trimIndent())

    importProject()

    val taskData: TaskData = ExternalSystemApiUtil
                               .findProjectTasks(myProject, GradleConstants.SYSTEM_ID, "$projectPath/projectA")
                               .find { it.name.contains("hello") } ?: throw AssertionFailedError("Task 'hello' not found")

    val output = runTask(taskData)
    assertThat(output).contains("expected!")
  }

  private fun runTask(taskData: TaskData, scriptParameters: String = ""): String {
    val taskExecutionInfo = ExternalSystemActionUtil.buildTaskInfo(taskData)
    taskExecutionInfo.settings.scriptParameters = scriptParameters

    val notificationManager = ApplicationManager.getApplication().getService(
      ExternalSystemProgressNotificationManager::class.java)
    val taskOutput = java.lang.StringBuilder()
    val listener = object : ExternalSystemTaskNotificationListener {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
        taskOutput.append(text)
      }
    }
    notificationManager.addNotificationListener(listener)
    try {
      runTask(taskExecutionInfo.settings, taskExecutionInfo.executorId, myProject, GradleConstants.SYSTEM_ID, null,
              ProgressExecutionMode.NO_PROGRESS_SYNC)
    }
    finally {
      notificationManager.removeNotificationListener(listener)
    }
    return taskOutput.toString()
  }

  private fun collectGradlePerformanceEvents(expectedEventCount: Int, runnable: () -> Unit): List<LogEvent> {
    val latch = CountDownLatch(expectedEventCount)
    val recordedEvents = CopyOnWriteArrayList<LogEvent>()
    FUCollectorTestCase
      .listenForEvents("FUS", getTestRootDisposable(), Consumer<LogEvent> {
        if (it.group.id == GradleTaskExecutionCollector.GROUP.id) {
          recordedEvents.add(it)
          latch.countDown()
        }
      }, runnable)
    latch.await(10, TimeUnit.SECONDS)
    return recordedEvents
  }

  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(BASE_GRADLE_VERSION))
  }
}