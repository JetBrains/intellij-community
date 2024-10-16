// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.plugins.gradle.service.execution.statistics.GradleTaskExecutionHandler
import org.jetbrains.plugins.gradle.service.execution.statistics.GradleTaskExecutionListener
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.lang.ref.WeakReference

class GradleTaskExecutionMeasuringExtension : GradleExecutionHelperExtension {

  override fun prepareForExecution(id: ExternalSystemTaskId,
                                   operation: LongRunningOperation,
                                   settings: GradleExecutionSettings,
                                   buildEnvironment: BuildEnvironment?) {
    val gradleVersion = buildEnvironment?.gradle?.gradleVersion
    if (gradleVersion == null || GradleVersionUtil.isGradleOlderThan(gradleVersion, "5.1")) {
      return
    }
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) {
      return
    }
    val handler = GradleTaskExecutionHandler(id.id, WeakReference(id.findProject()))
    val router = GradleTaskExecutionListener(handler)
    operation.addProgressListener(ProgressListener { router.route(it) }, OperationType.TASK)
    ExternalSystemProgressNotificationManager.getInstance()
      .addNotificationListener(id, object : ExternalSystemTaskNotificationListener {
        override fun onEnd(id: ExternalSystemTaskId) = router.flush()
      })
  }
}
