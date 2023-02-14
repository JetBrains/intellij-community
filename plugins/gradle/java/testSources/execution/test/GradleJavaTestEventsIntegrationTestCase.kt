// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test

import com.intellij.openapi.externalSystem.model.task.*
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.registry.Registry
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Condition
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID

abstract class GradleJavaTestEventsIntegrationTestCase : GradleProjectTestCase() {

  override fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    super.test(gradleVersion, fixtureBuilder) {
      val testLauncherApi = Registry.get("gradle.testLauncherAPI.enabled")
      if (testLauncherAPISupported()) {
        testLauncherApi.setValue(true)
      }
      try {
        test()
      }
      finally {
        if (testLauncherAPISupported()) {
          testLauncherApi.setValue(false)
        }
      }
    }
  }

  fun testLauncherAPISupported(): Boolean {
    return isGradleAtLeast("6.1")
  }

  fun executeTasks(vararg tasks: String, configure: GradleExecutionSettings.() -> Unit = {}): LoggingESOutputListener {
    val listener = LoggingESOutputListener()
    executeTasks(listener, *tasks, configure = configure)
    return listener
  }

  fun executeTasks(
    listener: LoggingESOutputListener,
    vararg tasks: String,
    configure: GradleExecutionSettings.() -> Unit = {}
  ) {
    val id = ExternalSystemTaskId.create(SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
    val settings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(project, projectPath, SYSTEM_ID)
    settings.configure()
    GradleTaskManager().executeTasks(id, tasks.toList(), projectPath, settings, null, listener)
  }

  class LoggingESOutputListener(
    private val delegate: LoggingESStatusChangeListener = LoggingESStatusChangeListener()
  ) : ExternalSystemTaskNotificationListenerAdapter(delegate) {

    val eventLog = mutableListOf<String>()

    val testsDescriptors: List<TestOperationDescriptor>
      get() = delegate.testsDescriptors

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      addEventLogLines(text, eventLog)
    }

    private fun addEventLogLines(text: String, eventLog: MutableList<String>) {
      text.split("<ijLogEol/>").mapTo(eventLog) { it.trim('\r', '\n', ' ') }
    }
  }

  class LoggingESStatusChangeListener : ExternalSystemTaskNotificationListenerAdapter() {
    private val eventLog = mutableListOf<ExternalSystemTaskNotificationEvent>()

    val testsDescriptors: List<TestOperationDescriptor>
      get() = eventLog
        .filterIsInstance<ExternalSystemTaskExecutionEvent>()
        .map { it.progressEvent.descriptor }
        .filterIsInstance<TestOperationDescriptor>()

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
      eventLog.add(event)
    }
  }

  companion object {
    fun <T : AbstractThrowableAssert<*, *>> T.`is`(message: String, predicate: (String) -> Boolean): T = apply {
      `is`(Condition({ predicate(it.message ?: "") }, message))
    }
  }
}
