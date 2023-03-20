// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleImportingUtil")
@file:ApiStatus.Internal

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
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


fun getGradleReloadOperation(externalProjectPath: String, parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleReloadOperation(parentDisposable) { it == externalProjectPath }
}

fun getGradleReloadOperation(parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleReloadOperation(parentDisposable) { true }
}

fun getGradleExecutionOperation(parentDisposable: Disposable): ObservableOperationTrace {
  return getGradleExecutionOperation(parentDisposable) { true }
}

private fun getGradleReloadOperation(parentDisposable: Disposable, isRelevant: (String?) -> Boolean): ObservableOperationTrace {
  val operation = AtomicOperationTrace(name = "Gradle Reload")
  val executionIds = HashMap<ExternalSystemTaskId, OperationExecutionId>()
  whenExternalSystemTaskStarted(parentDisposable) { id, path ->
    if (isResolveTask(id) && isRelevant(path)) {
      val executionId = OperationExecutionId.createId {
        putData(OperationExecutionContext.createKey("ID"), id)
        putData(OperationExecutionContext.createKey("PATH"), path)
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
        if (isRelevant(path)) {
          Disposer.dispose(loadDisposable)
          operation.traceFinish(executionId, loadStatus)
        }
      }
    }
  }
  return operation
}

private fun getGradleExecutionOperation(parentDisposable: Disposable, isRelevant: (String?) -> Boolean): ObservableOperationTrace {
  val operation = AtomicOperationTrace(name = "Gradle Execution")
  val executionIds = HashMap<ExternalSystemTaskId, OperationExecutionId>()
  whenExternalSystemTaskStarted(parentDisposable) { id, path ->
    if (isExecuteTask(id) && isRelevant(path)) {
      val executionId = OperationExecutionId.createId {
        putData(OperationExecutionContext.createKey("ID"), id)
        putData(OperationExecutionContext.createKey("PATH"), path)
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

private fun isResolveTask(id: ExternalSystemTaskId): Boolean {
  if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
    val task = ApplicationManager.getApplication()
      .getService(ExternalSystemProcessingManager::class.java)
      .findTask(id)
    if (task is ExternalSystemResolveProjectTask) {
      return !task.isPreviewMode
    }
  }
  return false
}

private fun isExecuteTask(id: ExternalSystemTaskId): Boolean {
  return id.type == ExternalSystemTaskType.EXECUTE_TASK
}

private fun whenExternalSystemTaskStarted(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskId, String?) -> Unit
) {
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        action(id, workingDir)
      }
    }, parentDisposable)
}

private fun whenExternalSystemTaskFinished(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskId, OperationExecutionStatus) -> Unit
) {
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
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