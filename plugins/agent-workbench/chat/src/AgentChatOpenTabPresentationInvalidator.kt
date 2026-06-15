// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec community/plugins/agent-workbench/spec/chat/agent-chat-editor.spec.md
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationChangeSet
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AgentChatOpenTabPresentationInvalidator {
  /**
   * Repaints open chat editors whose presentation key was affected by the change set.
   *
   * The resolved presentation is written back into the file's bootstrap fields so that
   * each repaint has a fast source for its title/activity and the next editor-state save
   * keeps the latest presentation available after IDE restart.
   * For sub-agent tabs the resolved title is still the sub-agent's own label (per
   * [resolveAgentChatThreadPresentation]); only the activity is inherited from the
   * shared thread presentation. Tabs whose resolved state is unchanged are skipped.
   */
  suspend fun invalidate(changeSet: AgentSessionThreadPresentationChangeSet): Int {
    if (changeSet.isEmpty) return 0

    var updatedFiles = 0
    withContext(Dispatchers.EDT) {
      val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()
      for (chatFile in openTabsSnapshot.files()) {
        val key = chatFile.presentationKeyOrNull() ?: continue
        if (key !in changeSet.changedKeys && key !in changeSet.removedKeys) {
          continue
        }

        val resolvedPresentation = resolveAgentChatThreadPresentation(chatFile)
        val titleChanged = chatFile.updateBootstrapThreadTitle(resolvedPresentation.title)
        val activityChanged = chatFile.updateBootstrapThreadActivity(resolvedPresentation.activity)
        val needsRepaint = titleChanged || activityChanged || key in changeSet.removedKeys
        if (!needsRepaint) continue

        val managers = openTabsSnapshot.managersFor(chatFile)
        if (managers.isEmpty()) continue

        updatedFiles++
        for (manager in managers) {
          manager.updateFilePresentation(chatFile)
        }
      }
    }

    return updatedFiles
  }
}
