// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.formatAgentSessionRelativeTimeShort
import com.intellij.agent.workbench.sessions.core.formatAgentSessionThreadTitle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.ui.components.JBList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.DefaultListModel

private const val MAX_EXISTING_TASKS = 200

internal class AgentPromptExistingTaskController(
  private val existingTaskListModel: DefaultListModel<ThreadEntry>,
  private val existingTaskList: JBList<ThreadEntry>,
  private val popupScope: CoroutineScope,
  private val sessionsMessageResolver: AgentPromptSessionsMessageResolver,
  private val onStateChanged: () -> Unit,
) {
  private val threadLoadVersion = AtomicInteger(0)
  private var existingTasksObservationJob: Job? = null
  private var allExistingTaskEntries: List<ThreadEntry> = emptyList()

  var selectedExistingTaskId: String? = null

  val entries: List<ThreadEntry>
    get() = allExistingTaskEntries

  fun hasLoadedEntries(): Boolean {
    return allExistingTaskEntries.isNotEmpty()
  }

  fun onUserSelected(selected: ThreadEntry) {
    selectedExistingTaskId = selected.id
  }

  fun clearSelection() {
    selectedExistingTaskId = null
  }

  fun selectedEntry(): ThreadEntry? {
    val selectedId = selectedExistingTaskId ?: return null
    return allExistingTaskEntries.firstOrNull { entry -> entry.id == selectedId }
  }

  fun dispose() {
    existingTasksObservationJob?.cancel()
    existingTasksObservationJob = null
  }

  fun reload(
    selectedProviderEntry: ProviderEntry?,
    launcher: AgentPromptLauncherBridge?,
    projectPath: String?,
    isPopupActive: () -> Boolean,
  ) {
    existingTasksObservationJob?.cancel()
    existingTasksObservationJob = null

    if (selectedProviderEntry == null) {
      updateListState(AgentPromptBundle.message("popup.error.no.providers"))
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      onStateChanged()
      return
    }

    if (!selectedProviderEntry.isCliAvailable) {
      updateListState(AgentPromptBundle.message("popup.error.provider.unavailable", selectedProviderEntry.displayName))
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      onStateChanged()
      return
    }

    if (launcher == null) {
      updateListState(AgentPromptBundle.message("popup.error.no.launcher"))
      selectedExistingTaskId = null
      onStateChanged()
      return
    }

    if (projectPath == null) {
      updateListState(AgentPromptBundle.message("popup.error.project.path"))
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      onStateChanged()
      return
    }

    updateListState(AgentPromptBundle.message("popup.existing.loading"))
    allExistingTaskEntries = emptyList()

    val requestVersion = threadLoadVersion.incrementAndGet()
    existingTasksObservationJob = popupScope.launch {
      launcher.observeExistingThreads(projectPath = projectPath, provider = selectedProviderEntry.bridge.provider)
        .onStart {
          launcher.refreshExistingThreads(projectPath = projectPath, provider = selectedProviderEntry.bridge.provider)
        }
        .catch {
          if (!isPopupActive() || requestVersion != threadLoadVersion.get()) {
            return@catch
          }
          allExistingTaskEntries = emptyList()
          selectedExistingTaskId = null
          existingTaskListModel.clear()
          existingTaskList.emptyText.text = AgentPromptBundle.message("popup.existing.error")
          onStateChanged()
        }
        .collectLatest { snapshot ->
          if (!isPopupActive() || requestVersion != threadLoadVersion.get()) {
            return@collectLatest
          }
          applySnapshot(snapshot)
        }
    }
  }

  fun applySnapshot(snapshot: AgentPromptExistingThreadsSnapshot) {
    if (snapshot.hasError) {
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      existingTaskListModel.clear()
      existingTaskList.emptyText.text = AgentPromptBundle.message("popup.existing.error")
      onStateChanged()
      return
    }

    val loaded = formatExistingTaskEntries(
      snapshot = snapshot,
      now = System.currentTimeMillis(),
      nowLabel = sessionsMessageResolver.resolve("toolwindow.time.now") ?: AgentPromptBundle.message("popup.time.now"),
      unknownLabel = sessionsMessageResolver.resolve("toolwindow.time.unknown") ?: AgentPromptBundle.message("popup.time.unknown"),
      fallbackTitle = { idPrefix ->
        sessionsMessageResolver.resolve("toolwindow.thread.fallback.title", null, idPrefix)
          ?: AgentPromptBundle.message("popup.existing.fallback.title", idPrefix)
      },
    )
    allExistingTaskEntries = loaded
    if (selectedExistingTaskId != null && loaded.none { entry -> entry.id == selectedExistingTaskId }) {
      selectedExistingTaskId = null
    }
    existingTaskListModel.clear()
    loaded.forEach { entry -> existingTaskListModel.addElement(entry) }
    if (loaded.isEmpty()) {
      existingTaskList.emptyText.text = when {
        snapshot.isLoading || !snapshot.hasLoaded -> AgentPromptBundle.message("popup.existing.loading")
        else -> AgentPromptBundle.message("popup.existing.empty")
      }
    }
    else {
      existingTaskList.emptyText.clear()
      val selectedIdx = loaded.indexOfFirst { entry -> entry.id == selectedExistingTaskId }
      if (selectedIdx >= 0) {
        existingTaskList.selectedIndex = selectedIdx
      }
    }
    onStateChanged()
  }

  private fun updateListState(message: @Nls String) {
    existingTaskListModel.clear()
    existingTaskList.emptyText.text = message
  }
}

internal fun formatExistingTaskEntries(
  snapshot: AgentPromptExistingThreadsSnapshot,
  now: Long,
  nowLabel: String,
  unknownLabel: String,
  fallbackTitle: (String) -> String,
): List<ThreadEntry> {
  return snapshot.threads
    .asSequence()
    .sortedByDescending(AgentSessionThread::updatedAt)
    .take(MAX_EXISTING_TASKS)
    .map { thread ->
      ThreadEntry(
        id = thread.id,
        displayText = formatAgentSessionThreadTitle(threadId = thread.id, title = thread.title, fallbackTitle = fallbackTitle),
        secondaryText = "  " + formatAgentSessionRelativeTimeShort(
          timestamp = thread.updatedAt,
          now = now,
          nowLabel = nowLabel,
          unknownLabel = unknownLabel,
        ),
        activity = thread.activity,
      )
    }
    .toList()
}
