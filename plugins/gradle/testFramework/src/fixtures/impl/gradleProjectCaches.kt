// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures.impl

import com.intellij.gradle.testFramework.util.refreshAndAwait
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitOpenProjectActivity
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskFinished

/**
 * Opens [projectRoot] once and saves the opened project, so that the `.idea` directory and the workspace model
 * caches are persisted next to the project sources.
 *
 * This is where the one and only Gradle import of a reusable project happens: opening a project directory that
 * has never been opened before triggers the import implicitly. Every later open of the same directory is served
 * from these caches and performs no import at all.
 */
internal suspend fun createGradleProjectCaches(projectRoot: VirtualFile) {
  closeOpenedProjectsIfFailAsync {
    assertExecutionStatusIsSuccess {
      awaitOpenProjectActivity {
        openProjectAsync(projectRoot)
      }
    }
  }.useProjectAsync(save = true) {
    projectRoot.refreshAndAwait()
  }
}

private suspend fun <R> assertExecutionStatusIsSuccess(action: suspend () -> R): R {
  Disposer.newDisposable().use { disposable ->
    var status: OperationExecutionStatus? = null
    whenExternalSystemTaskFinished(disposable) { _, newStatus ->
      status = newStatus
    }
    val result = action()
    when (val status = status) {
      null -> throw AssertionError("Execution isn't completed")
      is OperationExecutionStatus.Failure -> throw AssertionError("Execution is failed", status.cause)
      is OperationExecutionStatus.Cancel -> throw AssertionError("Execution is cancelled")
      is OperationExecutionStatus.Success -> {}
    }
    return result
  }
}
