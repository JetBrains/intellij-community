// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.externalSystem.util.runInEdtAndGet
import com.intellij.openapi.externalSystem.util.runInEdtAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runAll
import com.intellij.util.createException
import org.jetbrains.plugins.gradle.util.waitForProjectReload

fun openProject(projectRoot: VirtualFile): Project {
  return runInEdtAndGet {
    ProjectUtil.openOrImport(projectRoot.toNioPath())
  }
}

fun Project.closeProject(save: Boolean = false) {
  runInEdtAndWait {
    val projectManager = ProjectManagerEx.getInstanceEx()
    runAll(
      { if (save) StoreUtil.saveSettings(this, forceSavingAllSettings = true) },
      { projectManager.forceCloseProject(this) }
    )
  }
}

fun openProjectAndWait(projectRoot: VirtualFile): Project {
  var project: Project? = null
  return runCatching {
    waitForProjectReload {
      openProject(projectRoot)
        .also { project = it }
    }
  }.onFailureCatching {
    project?.closeProject()
  }.getOrThrow()
}

fun <T> Result<T>.onFailureCatching(action: (Throwable) -> Unit): Result<T> {
  val exception = exceptionOrNull() ?: return this
  val secondaryException = runCatching { action(exception) }.exceptionOrNull()
  val compound = createException(listOf(exception, secondaryException))!!
  return Result.failure(compound)
}
