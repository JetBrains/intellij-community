// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.thread.view.AgentThreadViewTabSelection
import com.intellij.agent.workbench.thread.view.toAgentThreadViewTabSelection
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.parseAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.FocusWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.event.FocusEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class AgentThreadViewUnreadAcknowledgementService(
  project: Project,
  private val serviceScope: CoroutineScope,
) : Disposable {
  private val fileEditorManager = FileEditorManager.getInstance(project)
  private val pendingCloseJobs = ConcurrentHashMap<String, Job>()
  private var selectedThreadViewFocusWatch: SelectedThreadViewFocusWatch? = null

  init {
    project.messageBus.connect(this).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          if (event.oldFile != event.newFile) {
            markThreadViewFileThreadAsRead(event.oldFile)
          }
          markThreadViewFileThreadAsRead(event.newFile)
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          val selection = file.toAgentThreadViewTabSelection() ?: return
          if (source.isFileOpen(file) || isOpenInAnyProject(file)) {
            cancelPendingCloseJob(selection)
            return
          }
          if (file.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) == true) {
            schedulePendingCloseConfirmation(file, selection)
            return
          }
          markThreadViewSelectionThreadAsRead(selection)
        }
      }
    )
    serviceScope.launch(Dispatchers.UI) {
      fileEditorManager.selectedEditorFlow.collect { editor ->
        updateSelectedThreadViewFocusWatch(editor)
      }
    }
  }

  override fun dispose() {
    pendingCloseJobs.values.forEach { job -> job.cancel() }
    pendingCloseJobs.clear()
    clearSelectedThreadViewFocusWatch()
  }

  private fun updateSelectedThreadViewFocusWatch(editor: FileEditor?) {
    if (selectedThreadViewFocusWatch?.editor == editor) {
      return
    }
    clearSelectedThreadViewFocusWatch()
    val selectedEditor = editor ?: return
    if (selectedEditor.file.toAgentThreadViewTabSelection() == null) {
      return
    }
    val watcher = object : FocusWatcher() {
      override fun focusLostImpl(e: FocusEvent) {
        scheduleMarkSelectedThreadViewEditorAsReadIfFocusMovedAway(selectedEditor)
      }
    }
    watcher.install(selectedEditor.component)
    selectedThreadViewFocusWatch = SelectedThreadViewFocusWatch(selectedEditor, watcher)
  }

  private fun clearSelectedThreadViewFocusWatch() {
    selectedThreadViewFocusWatch?.let { watch ->
      watch.watcher.deinstall(watch.editor.component)
    }
    selectedThreadViewFocusWatch = null
  }

  private fun scheduleMarkSelectedThreadViewEditorAsReadIfFocusMovedAway(editor: FileEditor) {
    serviceScope.launch(Dispatchers.UI) {
      if (selectedThreadViewFocusWatch?.editor != editor) {
        return@launch
      }
      markAgentThreadViewSelectionThreadAsReadAfterFocusLostIfUnread(
        selection = editor.file.toAgentThreadViewTabSelection(),
        isSelectedEditorStillFocused = fileEditorManager.focusedEditor == editor,
        state = service<AgentSessionReadService>().stateFlow().value,
        markThreadAsRead = { path, provider, threadId, updatedAt ->
          service<AgentSessionRefreshService>().markThreadAsRead(path, provider, threadId, updatedAt)
        },
      )
    }
  }

  private fun markThreadViewFileThreadAsRead(file: VirtualFile?) {
    markThreadViewSelectionThreadAsRead(file.toAgentThreadViewTabSelection())
  }

  private fun markThreadViewSelectionThreadAsRead(selection: AgentThreadViewTabSelection?) {
    selection ?: return
    markAgentThreadViewSelectionThreadAsReadIfUnread(
      selection = selection,
      state = service<AgentSessionReadService>().stateFlow().value,
      markThreadAsRead = { path, provider, threadId, updatedAt ->
        service<AgentSessionRefreshService>().markThreadAsRead(path, provider, threadId, updatedAt)
      },
    )
  }

  private fun schedulePendingCloseConfirmation(file: VirtualFile, selection: AgentThreadViewTabSelection) {
    cancelPendingCloseJob(selection)
    val job = serviceScope.launch(Dispatchers.UI) {
      repeat(PENDING_CLOSE_CONFIRMATION_RECHECK_COUNT) {
        delay(PENDING_CLOSE_CONFIRMATION_RECHECK_INTERVAL_MS.milliseconds)
        if (isOpenInAnyProject(file)) {
          return@launch
        }
      }
      pendingCloseJobs.remove(selection.closeJobKey())
      if (!isOpenInAnyProject(file)) {
        markThreadViewSelectionThreadAsRead(selection)
      }
    }
    pendingCloseJobs[selection.closeJobKey()] = job
    job.invokeOnCompletion {
      pendingCloseJobs.remove(selection.closeJobKey(), job)
    }
  }

  private fun cancelPendingCloseJob(selection: AgentThreadViewTabSelection) {
    pendingCloseJobs.remove(selection.closeJobKey())?.cancel()
  }

  private data class SelectedThreadViewFocusWatch(
    @JvmField val editor: FileEditor,
    @JvmField val watcher: FocusWatcher,
  )
}

internal fun markAgentThreadViewSelectionThreadAsReadAfterFocusLostIfUnread(
  selection: AgentThreadViewTabSelection?,
  isSelectedEditorStillFocused: Boolean,
  state: AgentSessionsState,
  markThreadAsRead: (String, AgentSessionProvider, String, Long) -> Unit,
): Boolean {
  if (isSelectedEditorStillFocused) {
    return false
  }
  selection ?: return false
  return markAgentThreadViewSelectionThreadAsReadIfUnread(
    selection = selection,
    state = state,
    markThreadAsRead = markThreadAsRead,
  )
}

internal fun markAgentThreadViewSelectionThreadAsReadIfUnread(
  selection: AgentThreadViewTabSelection,
  state: AgentSessionsState,
  markThreadAsRead: (String, AgentSessionProvider, String, Long) -> Unit,
): Boolean {
  val provider = AgentSessionProvider.fromOrNull(
    parseAgentThreadIdentity(selection.threadIdentity)?.providerId ?: return false
  ) ?: return false
  val path = normalizeAgentWorkbenchPath(selection.projectPath)
  val thread = resolveAgentSessionPathState(state, path)
                 ?.threads
                 ?.firstOrNull { thread ->
                   thread.provider == provider &&
                   thread.id == selection.threadId &&
                   thread.hasUnreadActivitySignal()
                 }
               ?: return false
  markThreadAsRead(path, provider, thread.id, thread.updatedAt)
  return true
}

private fun isOpenInAnyProject(file: VirtualFile): Boolean {
  return ProjectManager.getInstance().openProjects.any { openProject ->
    !openProject.isDisposed && FileEditorManager.getInstance(openProject).isFileOpen(file)
  }
}

private fun AgentThreadViewTabSelection.closeJobKey(): String {
  return "$projectPath\u0000$threadIdentity\u0000$threadId\u0000${subAgentId.orEmpty()}"
}

private const val PENDING_CLOSE_CONFIRMATION_RECHECK_INTERVAL_MS = 50L
private const val PENDING_CLOSE_CONFIRMATION_RECHECK_COUNT = 10
