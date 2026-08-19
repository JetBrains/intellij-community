// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
private class ProgressScopeProvider(val cs: CoroutineScope)

internal fun launchCoverageDataRenewal(
  project: Project,
  action: Runnable,
  onSuccess: Runnable,
  onCancel: Runnable,
) {
  project.service<ProgressScopeProvider>().cs.launch(Dispatchers.Default) {
    try {
      withBackgroundProgress(project, CoverageBundle.message("coverage.view.loading.data")) {
        action.run()
      }
      withContext(Dispatchers.EDT) {
        if (!project.isDisposed) onSuccess.run()
      }
    }
    catch (e: Throwable) {
      withContext(NonCancellable + Dispatchers.EDT) {
        if (!project.isDisposed) onCancel.run()
      }
      throw e
    }
  }
}
