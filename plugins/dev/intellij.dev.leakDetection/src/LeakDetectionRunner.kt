// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/** Runs [ProjectLeakDetector] on a background thread under a progress indicator, then reports the results. */
internal fun runLeakDetectionInBackground(project: Project?) {
  object : Task.Backgroundable(project, DevLeakDetectionBundle.message("progress.title.detecting.leaks"), true) {
    private var leaks: List<LeakInfo> = emptyList()

    override fun run(indicator: ProgressIndicator) {
      leaks = ProjectLeakDetector().detect()
    }

    override fun onSuccess() {
      LeakReporter.report(project, leaks)
    }
  }.queue()
}
