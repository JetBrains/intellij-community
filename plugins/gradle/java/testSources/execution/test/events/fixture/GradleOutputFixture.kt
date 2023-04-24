// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events.fixture

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptorImpl
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.util.containers.addIfNotNull
import org.assertj.core.api.Assertions
import org.assertj.core.api.ListAssert
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.util.getGradleTaskExecutionOperation
import java.util.function.Function

class GradleOutputFixture(
  private val project: Project
) : IdeaTestFixture {

  private lateinit var fixtureDisposable: Disposable

  private lateinit var taskExecutionLeakTracker: OperationLeakTracker

  private lateinit var output: Output

  override fun setUp() {
    fixtureDisposable = Disposer.newDisposable()

    taskExecutionLeakTracker = OperationLeakTracker { getGradleTaskExecutionOperation(project, it) }
    taskExecutionLeakTracker.setUp()

    installGradleEventsListener()
  }

  override fun tearDown() {
    runAll(
      { taskExecutionLeakTracker.tearDown() },
      { Disposer.dispose(fixtureDisposable) }
    )
  }

  fun <R> assertExecutionOutputIsReady(action: () -> R): R {
    output = Output()
    return taskExecutionLeakTracker.withAllowedOperation(1, action)
  }

  fun assertTestEventContain(className: String, methodName: String?) {
    Assertions.assertThat(output.testDescriptors)
      .transform { it.className to it.methodName }
      .contains(className to methodName)
  }

  fun assertTestEventDoesNotContain(className: String, methodName: String?) {
    Assertions.assertThat(output.testDescriptors)
      .transform { it.className to it.methodName }
      .doesNotContain(className to methodName)
  }

  fun assertTestEventsWasNotReceived() {
    Assertions.assertThat(output.testDescriptors)
      .transform { it.className to it.methodName }
      .isEmpty()
  }

  private fun installGradleEventsListener() {
    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {

      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        val testsDescriptors = text.split("<ijLogEol/>")
          .map { it.trim('\r', '\n', ' ') }
          .filter { it.isNotEmpty() }
          .mapNotNull { parseTestOperationDescriptor(it) }
        output.testDescriptors.addAll(testsDescriptors)
      }

      override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        val testDescriptor = getTestOperationDescriptor(event)
        output.testDescriptors.addIfNotNull(testDescriptor)
      }
    }

    ExternalSystemProgressNotificationManager.getInstance()
      .addNotificationListener(listener, fixtureDisposable)
  }

  private fun getTestOperationDescriptor(event: ExternalSystemTaskNotificationEvent): TestOperationDescriptor? {
    val executionEvent = event as? ExternalSystemTaskExecutionEvent ?: return null
    val descriptor = executionEvent.progressEvent.descriptor as? TestOperationDescriptor ?: return null
    val className = descriptor.className ?: ""
    val methodName = descriptor.methodName?.removeSuffix("()") ?: ""
    return TestOperationDescriptorImpl("", -1, "", className, methodName)
  }

  private fun parseTestOperationDescriptor(descriptor: String): TestOperationDescriptor? {
    val className = StringUtil.substringAfter(descriptor, "' className='")
                      ?.let { StringUtil.substringBefore(it, "' />") }
                    ?: return null
    val methodName = StringUtil.substringAfter(descriptor, "<descriptor name='")
                       ?.let { StringUtil.substringBefore(it, "' displayName='") }
                       ?.removeSuffix("()")
                     ?: return null
    return TestOperationDescriptorImpl("", -1, "", className, methodName)
  }

  private class Output {

    val testDescriptors: MutableList<TestOperationDescriptor> = ArrayList()
  }

  companion object {

    fun <T, R> ListAssert<T>.transform(transform: (T) -> R): ListAssert<R> {
      return extracting(Function(transform)) as ListAssert<R>
    }
  }
}