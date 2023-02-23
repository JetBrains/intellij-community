// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptorImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.text.StringUtil
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions
import org.assertj.core.api.Condition
import org.assertj.core.api.ListAssert
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleAtLeast
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isSupportedJUnit5
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.function.Function

abstract class GradleJavaTestEventsIntegrationTestCase : GradleProjectTestCase() {

  val jUnitTestAnnotationClass: String
    get() = when (isSupportedJUnit5(gradleVersion)) {
      true -> "org.junit.jupiter.api.Test"
      else -> "org.junit.Test"
    }

  fun executeTasks(commandLine: String, hasFailingTests: Boolean = false, noMatchingTests: Boolean = false): LoggingESOutputListener {
    val listener = LoggingESOutputListener(gradleVersion)
    if (hasFailingTests) {
      Assertions.assertThatThrownBy { executeTasks(listener, commandLine) }
        .`is`("contain failed tests message") {
          "Test failed." in it ||
          "There were failing tests" in it
        }
    }
    else if (noMatchingTests) {
      Assertions.assertThatThrownBy { executeTasks(listener, commandLine) }
        .`is`("contain no matching tests message") {
          "No matching tests found in any candidate test task." in it ||
          "No tests found for given includes:" in it
        }
    }
    else {
      executeTasks(listener, commandLine)
    }
    return listener
  }

  private fun executeTasks(listener: LoggingESOutputListener, rawCommandLine: String) {
    val commandLine = GradleCommandLine.parse(rawCommandLine)
    val id = ExternalSystemTaskId.create(SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
    val settings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(project, projectPath, SYSTEM_ID)
    settings.putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
    settings.putUserData(GradleConstants.FORCE_TEST_EXECUTION, true)
    settings.withArguments(commandLine.options.tokens)
    GradleTaskManager().executeTasks(id, commandLine.tasks.tokens, projectPath, settings, null, listener)
  }

  fun assertTaskOrder(output: LoggingESOutputListener, vararg tasks: String) {
    val tasksLine = output.outputLog.first { it.startsWith("Tasks to be executed:") }
    val tasksIndices = ArrayList<Pair<Int, String>>()
    for (task in tasks) {
      val index = tasksLine.indexOf(task)
      if (index == -1) {
        throw AssertionError("Task $task wasn't executed. $tasksLine")
      }
      tasksIndices.add(index to task)
    }
    tasksIndices.sortBy { it.first }
    assertEquals(tasks.toList(), tasksIndices.map { it.second })
  }

  class LoggingESOutputListener(
    private val gradleVersion: GradleVersion
  ) : ExternalSystemTaskNotificationListenerAdapter() {

    val outputLog = mutableListOf<String>()
    private val eventLog = mutableListOf<ExternalSystemTaskNotificationEvent>()

    val testsDescriptors: List<TestOperationDescriptor>
      get() = when (isSupportedTestLauncher()) {
        true -> eventLog.mapNotNull { getTestOperationDescriptor(it) }
        else -> outputLog.mapNotNull { parseTestOperationDescriptor(it) }
      }

    private fun isSupportedTestLauncher(): Boolean {
      return gradleVersion.isGradleAtLeast("7.6")
    }

    private fun getTestOperationDescriptor(event: ExternalSystemTaskNotificationEvent): TestOperationDescriptor? {
      val executionEvent = event as? ExternalSystemTaskExecutionEvent ?: return null
      val descriptor = executionEvent.progressEvent.descriptor as? TestOperationDescriptor ?: return null
      return TestOperationDescriptorImpl(descriptor.displayName, -1, null, descriptor.className, descriptor.methodName?.removeSuffix("()"))
    }

    private fun parseTestOperationDescriptor(descriptor: String): TestOperationDescriptor? {
      val methodName = StringUtil.substringAfter(descriptor, "<descriptor name='")
                         ?.let { StringUtil.substringBefore(it, "' displayName='") }
                         ?.removeSuffix("()")
                       ?: return null
      val displayName = StringUtil.substringAfter(descriptor, "' displayName='")
                          ?.let { StringUtil.substringBefore(it, "' className='") }
                        ?: return null
      val className = StringUtil.substringAfter(descriptor, "' className='")
                        ?.let { StringUtil.substringBefore(it, "' />") }
                      ?: return null
      return TestOperationDescriptorImpl(displayName, -1, null, className, methodName)
    }

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      outputLog.addAll(
        text.split("<ijLogEol/>").asSequence()
          .map { it.trim('\r', '\n', ' ') }
          .filter { it.isNotEmpty() }
      )
    }

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
      eventLog.add(event)
    }
  }

  companion object {
    fun <T : AbstractThrowableAssert<*, *>> T.`is`(message: String, predicate: (String) -> Boolean): T = apply {
      `is`(Condition({ predicate(it.message ?: "") }, message))
    }

    fun <T, R> ListAssert<T>.transform(transform: (T) -> R): ListAssert<R> {
      return extracting(Function { transform(it) }) as ListAssert<R>
    }
  }
}
