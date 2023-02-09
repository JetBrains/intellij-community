// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.openProjectAsync
import com.intellij.util.createException
import org.jetbrains.plugins.gradle.util.awaitProjectReload

internal fun <T> Result<T>.onFailureCatching(action: (Throwable) -> Unit): Result<T> {
  val exception = exceptionOrNull() ?: return this
  val secondaryException = runCatching { action(exception) }.exceptionOrNull()
  val compound = createException(listOf(exception, secondaryException))!!
  return Result.failure(compound)
}

suspend fun openProjectAsyncAndWait(virtualFile: VirtualFile, vararg activities: ProjectActivity): Project {
  return closeOpenedProjectsIfFailAsync {
    awaitProjectReload {
      openProjectAsync(virtualFile, *activities)
    }
  }
}