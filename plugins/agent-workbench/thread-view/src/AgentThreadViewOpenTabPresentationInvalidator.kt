// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec plugins/ij-air/spec/thread-view/agent-thread-view.spec.md
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationChangeSet
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AgentThreadViewOpenTabPresentationInvalidator {
  /**
   * Repaints open threadView editors whose presentation key was affected by the change set.
   *
   * The resolved presentation is written back into the file's bootstrap fields so that
   * each repaint has a fast source for its title/activity and the next editor-state save
   * keeps the latest presentation available after IDE restart.
   * For sub-agent tabs the resolved title is still the sub-agent's own label (per
   * [resolveAgentThreadViewThreadPresentation]); the activity report is inherited from the
   * shared thread presentation.
   */
  suspend fun invalidate(changeSet: AgentSessionThreadPresentationChangeSet): Int {
    if (changeSet.isEmpty) return 0

    var updatedFiles = 0
    withContext(Dispatchers.EDT) {
      val openTabsSnapshot = collectOpenAgentThreadViewTabsSnapshot()
      for (threadViewFile in openTabsSnapshot.files()) {
        val key = threadViewFile.presentationKeyOrNull() ?: continue
        if (key !in changeSet.changedKeys && key !in changeSet.removedKeys) {
          continue
        }

        val resolvedPresentation = resolveAgentThreadViewThreadPresentation(threadViewFile)
        val titleChanged = threadViewFile.updateBootstrapThreadTitle(resolvedPresentation.title)
        val activityReport = if (key in changeSet.removedKeys) {
          AgentThreadActivityReport(threadViewFile.bootstrapThreadActivity)
        }
        else {
          resolvedPresentation.activityReport
        }
        val activityReportChanged = threadViewFile.updateBootstrapThreadActivityReport(activityReport)
        if (!titleChanged && !activityReportChanged && key !in changeSet.removedKeys) continue

        val managers = openTabsSnapshot.managersFor(threadViewFile)
        if (managers.isEmpty()) continue

        updatedFiles++
        for (manager in managers) {
          // Chrome activity can change while title and row activity remain unchanged.
          manager.updateFilePresentation(threadViewFile)
        }
      }
    }

    return updatedFiles
  }
}
