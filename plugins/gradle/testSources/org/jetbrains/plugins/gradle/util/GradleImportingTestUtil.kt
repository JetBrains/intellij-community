// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleImportingTestUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.concurrency.Promise
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * @param action or some async calls have to produce project reload
 *  for example invokeLater { refreshProject(project, spec) }
 * @throws java.lang.AssertionError if import is failed or isn't started
 */
fun <R> waitForMultipleProjectsReload(expectedProjects: List<Path>, action: ThrowableComputable<R, Throwable>): R =
  waitForTask(getProjectDataLoadPromise(expectedProjects), action)

fun <R> waitForProjectReload(action: ThrowableComputable<R, Throwable>): R =
  waitForTask(getProjectDataLoadPromise(), action)

fun <R> waitForTaskExecution(action: ThrowableComputable<R, Throwable>): R =
  waitForTask(getExecutionTaskFinishPromise(), action)

private fun <R> waitForTask(finishTaskPromise: Promise<*>, action: ThrowableComputable<R, Throwable>): R {
  val result = action.compute()
  invokeAndWaitIfNeeded {
    PlatformTestUtil.waitForPromise(finishTaskPromise, TimeUnit.MINUTES.toMillis(1))
  }
  return result
}