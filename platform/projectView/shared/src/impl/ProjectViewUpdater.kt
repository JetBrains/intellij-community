// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewUpdater {
  suspend fun continuouslyUpdatePane(pane: ProjectViewPaneModel, progressReporter: ProjectViewUpdaterProgressReporter)
}

/**
 * Lets a [ProjectViewUpdater] report the progress of its internal update queue so the model can
 * wait if necessary until all requested updates are applied. Every event the updater queues for
 * later processing must be reported via [eventSubmitted], and once queued events have been turned
 * into node updates they must be reported via [eventsProcessed].
 */
@ApiStatus.Experimental
interface ProjectViewUpdaterProgressReporter {
  /** Reports that a single update event has been submitted to the updater's queue. */
  fun eventSubmitted()

  /** Reports that [count] previously submitted events have been processed (turned into node updates). */
  fun eventsProcessed(count: Int)
}
