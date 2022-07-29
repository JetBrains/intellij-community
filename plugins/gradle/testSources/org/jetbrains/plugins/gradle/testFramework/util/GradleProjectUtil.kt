// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.createException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.plugins.gradle.util.getProjectDataLoadPromise
import kotlin.time.Duration.Companion.minutes

internal fun <T> Result<T>.onFailureCatching(action: (Throwable) -> Unit): Result<T> {
  val exception = exceptionOrNull() ?: return this
  val secondaryException = runCatching { action(exception) }.exceptionOrNull()
  val compound = createException(listOf(exception, secondaryException))!!
  return Result.failure(compound)
}

suspend fun openProjectAsyncAndWait(virtualFile: VirtualFile, vararg activities: ProjectPostStartupActivity): Project {
  val deferred = getProjectDataLoadPromise()
  return openProjectAsync(virtualFile, *activities)
    .withProjectAsync {
      withContext(Dispatchers.EDT) {
        withTimeout(10.minutes) {
          deferred.asDeferred().join()
        }
      }
    }
}