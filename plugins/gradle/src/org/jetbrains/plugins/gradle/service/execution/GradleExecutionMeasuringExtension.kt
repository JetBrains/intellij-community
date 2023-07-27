// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleOlderThan
import org.jetbrains.plugins.gradle.service.execution.fus.GradleExecutionStageFusHandler
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer

class GradleExecutionMeasuringExtension : GradleOperationHelperExtension {

  private val myInflightOperations: ConcurrentMap<ExternalSystemTaskId, ExternalSystemListener> = ConcurrentHashMap()

  override fun prepareForExecution(id: ExternalSystemTaskId,
                                   operation: LongRunningOperation,
                                   gradleExecutionSettings: GradleExecutionSettings,
                                   buildEnvironment: BuildEnvironment?) {
    val gradleVersion = buildEnvironment?.gradleVersion()
    if (gradleVersion == null || gradleVersion.isGradleOlderThan("5.1")) {
      return
    }
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    val listener = ExternalSystemListener(id) {
      val gradleListener = myInflightOperations.remove(it)
      if (gradleListener != null) {
        notificationManager.removeNotificationListener(gradleListener)
      }
    }
    myInflightOperations[id] = listener
    operation.addProgressListener(listener)
    notificationManager.addNotificationListener(listener)
  }

  override fun prepareForSync(operation: LongRunningOperation, resolverCtx: ProjectResolverContext) {}

  private fun BuildEnvironment.gradleVersion(): GradleVersion? = gradle?.gradleVersion?.let { GradleVersion.version(it) }

  private class ExternalSystemListener(
    val taskId: ExternalSystemTaskId,
    val disposer: Consumer<ExternalSystemTaskId>
  ) : ExternalSystemTaskNotificationListener, ProgressListener {

    private val router: TaskExecutionAggregatedRouter = TaskExecutionAggregatedRouter(GradleExecutionStageFusHandler(
      taskId.id,
      WeakReference(taskId.findProject())
    ))

    override fun statusChanged(event: ProgressEvent) = router.route(event)

    override fun onEnd(id: ExternalSystemTaskId) {
      if (taskId != id) {
        return
      }
      router.flush()
      disposer.accept(taskId)
    }

    override fun onStart(id: ExternalSystemTaskId) {}
    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {}
    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {}
    override fun beforeCancel(id: ExternalSystemTaskId) {}
    override fun onSuccess(id: ExternalSystemTaskId) {}
    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {}
    override fun onCancel(id: ExternalSystemTaskId) {}
  }
}
