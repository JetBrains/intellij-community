// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleImportingTestUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.TimeUnit


/**
 * @param action or some async calls have to produce project reload
 *  for example invokeLater { refreshProject(project, spec) }
 * @throws java.lang.AssertionError if import is failed or isn't started
 */
fun <R> waitForProjectReload(action: ThrowableComputable<R, Throwable>): R {
  val promise = getProjectDataLoadPromise()
  val result = action.compute()
  invokeAndWaitIfNeeded {
    PlatformTestUtil.waitForPromise(promise, TimeUnit.MINUTES.toMillis(1))
  }
  return result
}

private fun isResolveTask(id: ExternalSystemTaskId): Boolean {
  if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
    val task = ExternalSystemProcessingManager.getInstance().findTask(id)
    if (task is ExternalSystemResolveProjectTask) {
      return !task.isPreviewMode
    }
  }
  return false
}

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

private fun getProjectDataLoadPromise(): Promise<Project> {
  return getResolveTaskFinishPromise()
    .thenAsync(::getProjectDataLoadPromise)
}

private fun getResolveTaskFinishPromise(): Promise<Project> {
  val promise = AsyncPromise<Project>()
  val parentDisposable = Disposer.newDisposable()
  ExternalSystemProgressNotificationManager.getInstance()
    .addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onSuccess(id: ExternalSystemTaskId) {
        if (isResolveTask(id)) {
          Disposer.dispose(parentDisposable)
          promise.setResult(id.findProject()!!)
        }
      }

      override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        if (isResolveTask(id)) {
          Disposer.dispose(parentDisposable)
          promise.setError(e)
        }
      }

      override fun onCancel(id: ExternalSystemTaskId) {
        if (isResolveTask(id)) {
          Disposer.dispose(parentDisposable)
          promise.cancel()
        }
      }
    }, parentDisposable)
  return promise
}

private fun getProjectDataLoadPromise(project: Project): Promise<Project> {
  val promise = AsyncPromise<Project>()
  val parentDisposable = Disposer.newDisposable()
  val connection = project.messageBus.connect(parentDisposable)
  connection.subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
      Disposer.dispose(parentDisposable)
      invokeLater {
        promise.setResult(project)
      }
    }

    override fun onImportFailed(projectPath: String?) {
      Disposer.dispose(parentDisposable)
      promise.setError("Import failed for $projectPath")
    }
  })
  return promise
}