// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds an application-level coroutine scope for the leak-detection background work. */
@Service
internal class LeakDetectionRunner(private val coroutineScope: CoroutineScope) {

  /** Runs [ProjectLeakDetector] on a background thread under a progress indicator, then reports the results. */
  fun runLeakDetectionInBackground(project: Project?) {
    service<LeakDetectionRunner>().coroutineScope.launch {
      val leaks = if (project != null) {
        withBackgroundProgress(project, DevLeakDetectionBundle.message("progress.title.detecting.leaks"), cancellable = true) {
          ProjectLeakDetector().detect()
        }
      }
      else {
        withModalProgress(ModalTaskOwner.guess(),
                          DevLeakDetectionBundle.message("modal.progress.title.detecting.leaks"),
                          TaskCancellation.cancellable()) {
          ProjectLeakDetector().detect()
        }
      }

      withContext(Dispatchers.UI) {
        reporter().report(project, leaks)
      }
    }
  }

  fun reporter(): LeakReporter = LeakReporter(coroutineScope)

  companion object {
    fun getInstance(): LeakDetectionRunner = service()
  }
}
