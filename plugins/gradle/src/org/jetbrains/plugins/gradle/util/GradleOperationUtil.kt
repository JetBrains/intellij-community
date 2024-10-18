// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleImportingUtil")
@file:ApiStatus.Internal

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.observable.operation.OperationExecutionContext
import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus


private val ID_KEY = OperationExecutionContext.createKey<ExternalSystemTaskId>("ID")
private val PATH_KEY = OperationExecutionContext.createKey<String?>("PATH")

fun getGradleProjectReloadOperation(externalProjectPath: String, parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleProjectReloadOperation(parentDisposable) { _, path -> path == externalProjectPath }
}

fun getGradleProjectReloadOperation(project: Project, parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleProjectReloadOperation(parentDisposable) { id, _ -> id.findProject() == project }
}

fun getGradleProjectReloadOperation(parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleProjectReloadOperation(parentDisposable) { _, _ -> true }
}

fun getGradleTaskExecutionOperation(project: Project, parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleTaskExecutionOperation(parentDisposable) { id, _ -> id.findProject() == project }
}

fun getGradleTaskExecutionOperation(parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleTaskExecutionOperation(parentDisposable) { _, _ -> true }
}

fun getGradleProjectReloadOperation(
  parentDisposable: Disposable,
  isRelevant: (ExternalSystemTaskId, String?) -> Boolean
): ObservableOperationTrace {
  val operation = AtomicOperationTrace("Gradle Reload")
  val executionIds = HashMap<ExternalSystemTaskId, OperationExecutionId>()
  whenExternalSystemTaskStarted(parentDisposable) { id, path ->
    if (isResolveTask(id) && isRelevant(id, path)) {
      val executionId = OperationExecutionId.createId {
        putData(ID_KEY, id)
        putData(PATH_KEY, path)
      }
      executionIds[id] = executionId
      operation.traceStart(executionId)
    }
  }
  whenExternalSystemTaskFinished(parentDisposable) { id, status ->
    val executionId = executionIds.remove(id)
    if (executionId != null) {
      if (status !is OperationExecutionStatus.Success) {
        operation.traceFinish(executionId, status)
        return@whenExternalSystemTaskFinished
      }
      val project = id.findProject()
      if (project == null) {
        operation.traceFinish(executionId, OperationExecutionStatus.Cancel)
        return@whenExternalSystemTaskFinished
      }
      val loadDisposable = Disposer.newDisposable(parentDisposable)
      whenProjectDataLoadFinished(project, loadDisposable) { path, loadStatus ->
        if (isRelevant(id, path)) {
          Disposer.dispose(loadDisposable)
          operation.traceFinish(executionId, loadStatus)
        }
      }
    }
  }
  return operation
}

fun getGradleTaskExecutionOperation(
  parentDisposable: Disposable,
  isRelevant: (ExternalSystemTaskId, String?) -> Boolean
): ObservableOperationTrace {
  val operation = AtomicOperationTrace("Gradle Task Execution")
  val executionIds = HashMap<ExternalSystemTaskId, OperationExecutionId>()
  whenExternalSystemTaskStarted(parentDisposable) { id, path ->
    if (isExecuteTask(id) && isRelevant(id, path)) {
      val executionId = OperationExecutionId.createId {
        putData(ID_KEY, id)
        putData(PATH_KEY, path)
      }
      executionIds[id] = executionId
      operation.traceStart(executionId)
    }
  }
  whenExternalSystemTaskFinished(parentDisposable) { id, status ->
    val executionId = executionIds.remove(id)
    if (executionId != null) {
      operation.traceFinish(executionId, status)
    }
  }
  return operation
}

fun isResolveTask(id: ExternalSystemTaskId): Boolean {
  if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
    val processingManager = ExternalSystemProcessingManager.getInstance()
    val task = processingManager.findTask(id)
    if (task is ExternalSystemResolveProjectTask) {
      return !task.isPreviewMode
    }
  }
  return false
}

private fun isExecuteTask(id: ExternalSystemTaskId): Boolean {
  return id.type == ExternalSystemTaskType.EXECUTE_TASK
}

fun whenExternalSystemTaskStarted(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskId, String?) -> Unit
) {
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(object : ExternalSystemTaskNotificationListener {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        action(id, workingDir)
      }
    }, parentDisposable)
}

fun whenExternalSystemTaskFinished(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskId, OperationExecutionStatus) -> Unit
) {
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(object : ExternalSystemTaskNotificationListener {
      override fun onSuccess(id: ExternalSystemTaskId) {
        action(id, OperationExecutionStatus.Success)
      }

      override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        action(id, OperationExecutionStatus.Failure(e))
      }

      override fun onCancel(id: ExternalSystemTaskId) {
        action(id, OperationExecutionStatus.Cancel)
      }
    }, parentDisposable)
}

fun whenExternalSystemTaskOutputAdded(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskId, String, Boolean) -> Unit
) {
  val listener = object : ExternalSystemTaskNotificationListener {
    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      action(id, text, stdOut)
    }
  }
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(listener, parentDisposable)
}

fun whenExternalSystemEventReceived(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskNotificationEvent) -> Unit
) {
  val listener = object : ExternalSystemTaskNotificationListener {
    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
      action(event)
    }
  }
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(listener, parentDisposable)
}

private fun whenProjectDataLoadFinished(
  project: Project,
  parentDisposable: Disposable,
  action: (String?, OperationExecutionStatus) -> Unit
) {
  project.messageBus.connect(parentDisposable)
    .subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
      override fun onImportFailed(projectPath: String?, t: Throwable) {
        action(projectPath, OperationExecutionStatus.Failure(t))
      }

      override fun onFinalTasksFinished(projectPath: String?) {
        action(projectPath, OperationExecutionStatus.Success)
      }
    })
}