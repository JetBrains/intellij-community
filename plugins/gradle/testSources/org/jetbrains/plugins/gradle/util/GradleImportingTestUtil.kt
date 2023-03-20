// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleImportingTestUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.observable.operation.core.awaitOperation
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.use
import com.intellij.testFramework.observable.operation.core.waitForOperationAndPumpEdt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


fun <R> waitForProjectReload(externalProjectPath: String, action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForProjectReload").use { disposable ->
    getGradleReloadOperation(externalProjectPath, disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action = action)
  }
}

fun <R> waitForProjectReload(action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForAnyProjectReload").use { disposable ->
    getGradleReloadOperation(disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action = action)
  }
}

fun <R> waitForTaskExecution(action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForTaskExecution").use { disposable ->
    getGradleExecutionOperation(disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action = action)
  }
}

suspend fun <R> awaitProjectReload(action: suspend () -> R): R {
  return Disposer.newDisposable("awaitProjectReload").use { disposable ->
    getGradleReloadOperation(disposable)
      .awaitOperation(10.seconds, 10.minutes, action = action)
  }
}
