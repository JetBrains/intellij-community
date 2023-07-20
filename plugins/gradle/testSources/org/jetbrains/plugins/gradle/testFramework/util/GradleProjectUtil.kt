// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

internal inline fun <T> Result<T>.onFailureCatching(action: (Throwable) -> Unit): Result<T> {
  val exception = exceptionOrNull() ?: return this
  val secondaryException = runCatching { action(exception) }.exceptionOrNull()
  secondaryException?.let {
    exception.addSuppressed(it)
  }
  return Result.failure(exception)
}
