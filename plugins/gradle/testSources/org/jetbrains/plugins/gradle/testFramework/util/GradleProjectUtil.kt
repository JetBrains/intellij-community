// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.util.createException
import kotlinx.coroutines.runBlocking

internal fun Project.closeProject(save: Boolean = false) {
  val project = this
  runBlocking {
    ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = save)
  }
}

internal fun <T> Result<T>.onFailureCatching(action: (Throwable) -> Unit): Result<T> {
  val exception = exceptionOrNull() ?: return this
  val secondaryException = runCatching { action(exception) }.exceptionOrNull()
  val compound = createException(listOf(exception, secondaryException))!!
  return Result.failure(compound)
}
