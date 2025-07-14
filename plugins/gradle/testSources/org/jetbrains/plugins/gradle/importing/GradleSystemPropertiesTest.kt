// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import org.assertj.core.api.Assertions.assertThat
import com.google.gson.Gson
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class GradleSystemPropertiesTest: GradleImportingTestCase() {

  @Test
  @TargetVersions("7.6+")
  fun `Given IDE system properties When executing any task Then those properties are not passed to Gradle`() {
    val ideSystemProperties = mapOf("FOO" to "foo value", "FOO2" to "foo2 value")
    executeSimpleGradleTask(ideSystemProperties) { gradleSystemProperties ->
      assertThat(gradleSystemProperties).isNotEmpty
      assertThat(gradleSystemProperties).doesNotContainKeys(*ideSystemProperties.keys.toTypedArray())
    }
  }

  @Test
  @TargetVersions("7.0.2")
  fun `Given IDE system properties When executing any task Then those properties are passed to Gradle`() {
    val ideSystemProperties = mapOf("FOO" to "foo value", "FOO2" to "foo2 value")
    executeSimpleGradleTask(ideSystemProperties) { gradleSystemProperties ->
      assertThat(gradleSystemProperties).containsAllEntriesOf(ideSystemProperties)
    }
  }

  private fun executeSimpleGradleTask(
    ideSystemProperties: Map<String, String>,
    gradleSystemProperties: (Map<String, String>) -> Unit
  ) {
    registerIdeSystemProperties(ideSystemProperties)
    importProjectWithPrintSystemPropertiesTask()

    val notificationManager = ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager::class.java)
    val taskOutputSystemProperties = mutableMapOf<String, String>()
    val listener = createGradleTaskListener(taskOutputSystemProperties)
    notificationManager.addNotificationListener(listener)
    try {
      val module: Module = getModule("project")
      val settings = ExternalSystemTaskExecutionSettings().apply {
        externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        taskNames = listOf("printSystemProperties")
        externalSystemIdString = GradleConstants.SYSTEM_ID.id
        scriptParameters = "--quiet"
      }
      ExternalSystemUtil.runTask(
        settings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID, null, ProgressExecutionMode.NO_PROGRESS_SYNC
      )
    } finally {
      notificationManager.removeNotificationListener(listener)
      gradleSystemProperties(taskOutputSystemProperties)
    }
  }

  private fun registerIdeSystemProperties(ideSystemProperties: Map<String, String>) {
    ideSystemProperties.forEach {
      System.setProperty(it.key, it.value)
    }
  }

  private fun importProjectWithPrintSystemPropertiesTask() {
    importProject("""
      import groovy.json.JsonBuilder
      
      task printSystemProperties() {       
        doLast { println new JsonBuilder(System.getProperties()).toString() }
      }
    """.trimIndent())
  }

  private fun createGradleTaskListener(taskOutputSystemProperties: MutableMap<String, String>) =
    object : ExternalSystemTaskNotificationListener {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        try {
          Gson().fromJson<Map<String, String>>(text, Map::class.java)?.let {
            taskOutputSystemProperties.putAll(it)
          }
        } catch (ignore: Exception) {
          // do not fail test on extra lines in the output
        }
      }
    }
}