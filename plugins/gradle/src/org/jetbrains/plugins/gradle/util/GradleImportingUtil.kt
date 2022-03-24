// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleImportingUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import java.nio.file.Path
import kotlin.io.path.absolutePathString

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

@IntellijInternalApi
fun whenResolveTaskStarted(action: () -> Unit, parentDisposable: Disposable) {
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (isResolveTask(id)) {
          action()
        }
      }
    }, parentDisposable)
}

@IntellijInternalApi
fun getProjectDataLoadPromise(parentDisposable: Disposable): Promise<Project> {
  return getResolveTaskFinishPromise(parentDisposable)
    .thenAsync { project -> getProjectDataLoadPromise(project, null) }
}

@IntellijInternalApi
fun getExecutionTaskFinishPromise(parentDisposable: Disposable): Promise<Project> {
  return getExternalSystemTaskFinishPromise(parentDisposable) { it.type == ExternalSystemTaskType.EXECUTE_TASK }
}

@TestOnly
fun getProjectDataLoadPromise(): Promise<Project> {
  return getExternalSystemTaskFinishPromise(::isResolveTask)
    .thenAsync { project -> getProjectDataLoadPromise(project, null) }
}

/**
 * @param expectedProjects specific linked gradle projects paths to wait for
 */
@TestOnly
fun getProjectDataLoadPromise(expectedProjects: List<Path>): Promise<Project> {
  require(expectedProjects.isNotEmpty())

  return getExternalSystemTaskFinishPromise(::isResolveTask).thenAsync { project ->
      expectedProjects.map {
        val linkedProjectPath: String = FileUtil.toCanonicalPath(it.absolutePathString())
        getProjectDataLoadPromise(project, linkedProjectPath)
      }.all(project, false)
    }
}

@TestOnly
fun getExecutionTaskFinishPromise(): Promise<Project> {
  return getExternalSystemTaskFinishPromise { it.type == ExternalSystemTaskType.EXECUTE_TASK }
}

private fun getResolveTaskFinishPromise(parentDisposable: Disposable): Promise<Project> {
  return getExternalSystemTaskFinishPromise(parentDisposable, ::isResolveTask)
}

private fun getExternalSystemTaskFinishPromise(
  parentDisposable: Disposable,
  isRelevantTask: (ExternalSystemTaskId) -> Boolean
): Promise<Project> {
  val disposable = Disposer.newDisposable(parentDisposable, "")
  return getExternalSystemTaskFinishPromiseImpl(disposable, isRelevantTask)
}

private fun getExternalSystemTaskFinishPromise(
  isRelevantTask: (ExternalSystemTaskId) -> Boolean
): Promise<Project> {
  val disposable = Disposer.newDisposable("")
  return getExternalSystemTaskFinishPromiseImpl(disposable, isRelevantTask)
}

private fun getExternalSystemTaskFinishPromiseImpl(
  disposable: Disposable,
  isRelevantTask: (ExternalSystemTaskId) -> Boolean
): Promise<Project> {
  val promise = AsyncPromise<Project>()
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onSuccess(id: ExternalSystemTaskId) {
        if (isRelevantTask(id)) {
          Disposer.dispose(disposable)
          promise.setResult(id.findProject()!!)
        }
      }

      override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        if (isRelevantTask(id)) {
          Disposer.dispose(disposable)
          promise.setError(e)
        }
      }

      override fun onCancel(id: ExternalSystemTaskId) {
        if (isRelevantTask(id)) {
          Disposer.dispose(disposable)
          promise.cancel()
        }
      }
    }, disposable)
  return promise
}

private fun getProjectDataLoadPromise(project: Project, expectedProjectPath: String? = null): Promise<Project> {
  val promise = AsyncPromise<Project>()
  val parentDisposable = Disposer.newDisposable()
  val connection = project.messageBus.connect(parentDisposable)

  connection.subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
      if (expectedProjectPath == null || expectedProjectPath == projectPath) {
        Disposer.dispose(parentDisposable)
        invokeLater {
          promise.setResult(project)
        }
      }
    }

    override fun onImportFailed(projectPath: String?) {
      if (expectedProjectPath == null || expectedProjectPath == projectPath) {
        Disposer.dispose(parentDisposable)
        promise.setError("Import failed for $projectPath")
      }
    }
  })
  return promise
}