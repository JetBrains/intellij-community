// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemTaskProgressTextConfigurator
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import org.jetbrains.plugins.gradle.service.execution.GradleProgressIndicatorEventHelper.areGradleBuildProgressEventsSupported
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

internal class GradleProgressIndicatorHelper(
  private val id: ExternalSystemTaskId,
  private val effectiveSettings: GradleExecutionSettings,
  private val listener: ExternalSystemTaskNotificationListener
) {

  fun runWithProgressIndicator(consumer: Consumer<ExternalSystemTaskNotificationListener>) {
    if (!areGradleBuildProgressEventsSupported(effectiveSettings)) {
      // Don't run with indicator if build phase events are not supported
      consumer.accept(listener)
      return
    }

    val progressIndicator = AtomicReference<ProgressIndicator?>()
    val countDownLatch = CountDownLatch(1)
    ProgressManager.getInstance().run(object : Task.Backgroundable(id.findProject(), "Gradle build", false) {
      override fun run(indicator: ProgressIndicator) {
        progressIndicator.set(indicator)
        countDownLatch.await()
      }
    })

    val listenerWithProgressIndicator = object : ExternalSystemTaskNotificationListenerAdapter(listener) {
      override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        super.onStatusChange(event)
        if (progressIndicator.get() != null) {
          updateProgressIndicator(event, progressIndicator.get()!!)
        }
      }
    }

    try {
      consumer.accept(listenerWithProgressIndicator)
    }
    finally {
      countDownLatch.countDown()
    }
  }

  private fun updateProgressIndicator(event: ExternalSystemTaskNotificationEvent, indicator: ProgressIndicator) {
    ExternalSystemTaskProgressTextConfigurator.updateProgressIndicator(event, indicator) {
      ExternalSystemBundle.message("progress.update.text", GradleConstants.SYSTEM_ID.readableName, it)
    }
  }
}