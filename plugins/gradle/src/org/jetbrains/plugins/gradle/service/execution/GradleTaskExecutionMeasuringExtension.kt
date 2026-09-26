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
import org.jetbrains.plugins.gradle.service.execution.statistics.GradleTaskExecutionHandler
import org.jetbrains.plugins.gradle.service.execution.statistics.GradleTaskExecutionListener
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import java.lang.ref.WeakReference

class GradleTaskExecutionMeasuringExtension : GradleExecutionHelperExtension {

  override fun configureOperation(operation: LongRunningOperation, context: GradleExecutionContext) {
    if (GradleVersionUtil.isGradleOlderThan(context.gradleVersion, "5.1")) {
      return
    }
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) {
      return
    }
    val handler = GradleTaskExecutionHandler(context.taskId.id, WeakReference(context.project))
    val router = GradleTaskExecutionListener(handler)
    operation.addProgressListener(ProgressListener { router.route(it) }, OperationType.TASK)
    ExternalSystemProgressNotificationManager.getInstance()
      .addNotificationListener(context.taskId, object : ExternalSystemTaskNotificationListener {
        override fun onEnd(proojecPath: String, id: ExternalSystemTaskId) = router.flush()
      })
  }
}
