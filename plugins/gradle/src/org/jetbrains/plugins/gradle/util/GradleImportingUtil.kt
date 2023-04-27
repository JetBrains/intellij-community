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
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

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

fun getReloadProjectPromise(parentDisposable: Disposable): Promise<Project> {
  return getResolveTaskPromise(parentDisposable)
    .thenAsync { project -> getProjectDataLoadPromise(project, parentDisposable) { true } }
}

fun getResolveTaskPromise(parentDisposable: Disposable): Promise<Project> {
  return getExternalSystemTaskPromise(parentDisposable) { isResolveTask(it) }
}

fun getExecutionTaskPromise(
  parentDisposable: Disposable
): Promise<Project> {
  return getExternalSystemTaskPromise(parentDisposable) { it.type == ExternalSystemTaskType.EXECUTE_TASK }
}

private fun getExternalSystemTaskPromise(
  parentDisposable: Disposable,
  isRelevantTask: (ExternalSystemTaskId) -> Boolean
): Promise<Project> {
  val promise = AsyncPromise<Project>()
  val disposable = Disposer.newDisposable(parentDisposable, "ExternalSystemTaskPromise")
  whenExternalSystemTaskFinished(parentDisposable) { id, status ->
    if (isRelevantTask(id)) {
      Disposer.dispose(disposable)
      promise.complete(status) { id.findProject()!! }
    }
  }
  return promise
}

fun getProjectDataLoadPromise(project: Project, parentDisposable: Disposable, isRelevantTask: (String?) -> Boolean): Promise<Project> {
  val promise = AsyncPromise<Project>()
  val disposable = Disposer.newDisposable(parentDisposable, "ProjectDataLoadPromise")
  whenProjectDataLoadFinished(project, disposable) { path, status ->
    if (isRelevantTask(path)) {
      Disposer.dispose(disposable)
      promise.complete(status) { project }
    }
  }
  return promise
}

private fun <T> AsyncPromise<T>.complete(status: OperationExecutionStatus, result: () -> T) {
  when (status) {
    is OperationExecutionStatus.Success -> setResult(result())
    is OperationExecutionStatus.Failure -> setError(status.cause!!)
    is OperationExecutionStatus.Cancel -> cancel()
  }
}

fun whenResolveTaskStarted(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskId, String?) -> Unit
) {
  whenExternalSystemTaskStarted(parentDisposable) { id, path ->
    if (isResolveTask(id)) {
      action(id, path)
    }
  }
}

fun whenResolveTaskFinished(
  parentDisposable: Disposable,
  action: (ExternalSystemTaskId, OperationExecutionStatus) -> Unit
) {
  whenExternalSystemTaskFinished(parentDisposable) { id, status ->
    if (isResolveTask(id)) {
      action(id, status)
    }
  }
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