// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager

/**
 * Annotations presentation depends on the [UpToDateLineNumberProviderImpl].
 * We need to make sure that the annotation's preferred width is up to date.
 */
internal class AnnotationsLineStatusTrackerListener(private val project: Project) : LineStatusTrackerManager.Listener {
  override fun onTrackerBecomeValid(tracker: LineStatusTracker<*>) {
    ApplicationManager.getApplication().invokeLater(
      {
        AnnotateActionGroup.revalidateMarkupInEditors(project, tracker.document)
      }, project.disposed)
  }
}
