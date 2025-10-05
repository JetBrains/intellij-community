// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.addIfNotNull
import org.assertj.core.api.Assertions
import org.assertj.core.api.ListAssert
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionOutputFixture
import java.util.function.Function

class GradleExecutionOutputFixtureImpl : GradleExecutionOutputFixture {

  private lateinit var fixtureDisposable: Disposable

  private lateinit var output: Output

  override fun setUp() {
    fixtureDisposable = Disposer.newDisposable()

    installGradleEventsListener()
  }

  override fun tearDown() {
    Disposer.dispose(fixtureDisposable)
  }

  override fun assertTestEventContain(className: String, methodName: String?) {
    Assertions.assertThat(output.testDescriptors)
      .transform { it.className to it.methodName }
      .contains(className to methodName)
  }

  override fun assertTestEventDoesNotContain(className: String, methodName: String?) {
    Assertions.assertThat(output.testDescriptors)
      .transform { it.className to it.methodName }
      .doesNotContain(className to methodName)
  }

  override fun assertTestEventsWasNotReceived() {
    Assertions.assertThat(output.testDescriptors)
      .transform { it.className to it.methodName }
      .isEmpty()
  }

  private fun installGradleEventsListener() {
    val listener = object : ExternalSystemTaskNotificationListener {

      override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
        output = Output()
      }

      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
        output.testDescriptors.addAll(
          extractTestOperationDescriptors(text)
        )
      }

      override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        output.testDescriptors.addIfNotNull(
          getTestOperationDescriptor(event)
        )
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
    return TestOperationDescriptor("", -1, "", className, methodName)
  }

  private fun extractTestOperationDescriptors(text: String): List<TestOperationDescriptor> {
    val descriptors = ArrayList<TestOperationDescriptor>()
    for (rawDescriptor in text.split("<ijLogEol/>")) {
      val descriptor = parseTestOperationDescriptor(rawDescriptor)
      if (descriptor != null) {
        descriptors.add(descriptor)
      }
    }
    return descriptors
  }

  private fun parseTestOperationDescriptor(descriptor: String): TestOperationDescriptor? {
    val className = StringUtil.substringAfter(descriptor, "' className='")
                      ?.let { StringUtil.substringBefore(it, "' />") }
                    ?: return null
    val methodName = StringUtil.substringAfter(descriptor, "<descriptor name='")
                       ?.let { StringUtil.substringBefore(it, "' displayName='") }
                       ?.removeSuffix("()")
                     ?: return null
    return TestOperationDescriptor("", -1, "", className, methodName)
  }

  private class Output {

    val testDescriptors: MutableList<TestOperationDescriptor> = ArrayList()
  }

  companion object {

    @Suppress("UNCHECKED_CAST")
    fun <T, R> ListAssert<T>.transform(transform: (T) -> R): ListAssert<R> {
      return extracting(Function(transform)) as ListAssert<R>
    }
  }
}