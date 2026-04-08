// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.util.NlsSafe
import junit.framework.TestCase
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import java.io.IOException
import java.util.Collections

/**
 * @author Vladislav.Soroka
 */
class GradleEnvironmentTest : GradleImportingTestCase() {
  @Test
  @Throws(Exception::class)
  fun testGradleEnvCustomization() {
    val passedEnv = Collections.singletonMap("FOO", "foo value")
    val gradleEnv = StringBuilder()

    importAndRunTask(passedEnv, gradleEnv)

    val output = gradleEnv.toString().lines()
      .filter { !it.startsWith("Starting Gradle Daemon") && !it.startsWith("Gradle Daemon started") }
      .joinToString("\n")
      .trim()
    TestCase.assertEquals(DefaultGroovyMethods.toMapString(passedEnv), output)
  }

  @Throws(IOException::class)
  private fun importAndRunTask(passedEnv: MutableMap<String?, String?>, gradleEnv: StringBuilder) {
    importProject(
      """
                    task printEnv() {
                      doLast { println System.getenv().toMapString()}
                    }
                    """.trimIndent()
    )

    val settings = ExternalSystemTaskExecutionSettings()
    val module = getModule("project")
    settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
    settings.taskNames = mutableListOf<@NlsSafe String?>("printEnv")
    settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    settings.scriptParameters = "--quiet"
    settings.isPassParentEnvs = false
    settings.env = passedEnv
    val notificationManager = ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager::class.java)
    val listener: ExternalSystemTaskNotificationListener = object : ExternalSystemTaskNotificationListener {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
        gradleEnv.append(text)
      }
    }
    notificationManager.addNotificationListener(listener)
    try {
      ExternalSystemUtil.runTask(
        settings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID, null,
        ProgressExecutionMode.NO_PROGRESS_SYNC
      )
    }
    finally {
      notificationManager.removeNotificationListener(listener)
    }
  }
}
