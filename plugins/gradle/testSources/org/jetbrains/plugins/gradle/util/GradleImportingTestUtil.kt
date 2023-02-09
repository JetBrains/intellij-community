// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleImportingTestUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.use
import com.intellij.testFramework.concurrency.awaitPromise
import com.intellij.testFramework.concurrency.waitForPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import java.nio.file.Path

/**
 * @param action or some async calls have to produce project reload
 *  for example invokeLater { refreshProject(project, spec) }
 * @throws java.lang.AssertionError if import is failed or isn't started
 */
fun <R> waitForMultipleProjectsReload(expectedProjects: List<Path>, action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForMultipleProjectsReload").use { disposable ->
    getMultipleReloadProjectPromise(expectedProjects, disposable).waitForPromise(action = action)
  }
}

fun <R> waitForProjectReload(action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForProjectReload").use { disposable ->
    getReloadProjectPromise(disposable).waitForPromise(action = action)
  }
}

fun <R> waitForTaskExecution(action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForTaskExecution").use { disposable ->
    getExecutionTaskPromise(disposable).waitForPromise(action = action)
  }
}

suspend fun <R> awaitProjectReload(action: suspend () -> R): R {
  return Disposer.newDisposable("awaitProjectReload").use { disposable ->
    getReloadProjectPromise(disposable).awaitPromise(action = action)
  }
}

private fun getMultipleReloadProjectPromise(expectedProjects: List<Path>, parentDisposable: Disposable): Promise<Project> {
  require(expectedProjects.isNotEmpty())

  return getResolveTaskPromise(parentDisposable)
    .thenAsync { project ->
      expectedProjects.map { it.toCanonicalPath() }
        .map { path -> getProjectDataLoadPromise(project, parentDisposable) { it == path } }
        .all(project, false)
    }
}
