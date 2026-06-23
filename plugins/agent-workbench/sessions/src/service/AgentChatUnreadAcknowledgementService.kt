// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.chat.toAgentChatTabSelection
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.parseAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class AgentChatUnreadAcknowledgementService(
  project: Project,
  private val serviceScope: CoroutineScope,
) : Disposable {
  private val pendingCloseJobs = ConcurrentHashMap<String, Job>()

  init {
    project.messageBus.connect(this).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          if (event.oldFile != event.newFile) {
            markChatFileThreadAsRead(event.oldFile)
          }
          markChatFileThreadAsRead(event.newFile)
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          val selection = file.toAgentChatTabSelection() ?: return
          if (source.isFileOpen(file) || isOpenInAnyProject(file)) {
            cancelPendingCloseJob(selection)
            return
          }
          if (file.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) == true) {
            schedulePendingCloseConfirmation(file, selection)
            return
          }
          markChatSelectionThreadAsRead(selection)
        }
      }
    )
  }

  override fun dispose() {
    pendingCloseJobs.values.forEach { job -> job.cancel() }
    pendingCloseJobs.clear()
  }

  private fun markChatFileThreadAsRead(file: VirtualFile?) {
    markChatSelectionThreadAsRead(file.toAgentChatTabSelection())
  }

  private fun markChatSelectionThreadAsRead(selection: AgentChatTabSelection?) {
    selection ?: return
    markAgentChatSelectionThreadAsReadIfUnread(
      selection = selection,
      state = service<AgentSessionReadService>().stateFlow().value,
      markThreadAsRead = { path, provider, threadId, updatedAt ->
        service<AgentSessionRefreshService>().markThreadAsRead(path, provider, threadId, updatedAt)
      },
    )
  }

  private fun schedulePendingCloseConfirmation(file: VirtualFile, selection: AgentChatTabSelection) {
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
        markChatSelectionThreadAsRead(selection)
      }
    }
    pendingCloseJobs[selection.closeJobKey()] = job
    job.invokeOnCompletion {
      pendingCloseJobs.remove(selection.closeJobKey(), job)
    }
  }

  private fun cancelPendingCloseJob(selection: AgentChatTabSelection) {
    pendingCloseJobs.remove(selection.closeJobKey())?.cancel()
  }
}

internal fun markAgentChatSelectionThreadAsReadIfUnread(
  selection: AgentChatTabSelection,
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
                   thread.activity == AgentThreadActivity.UNREAD
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

private fun AgentChatTabSelection.closeJobKey(): String {
  return "$projectPath\u0000$threadIdentity\u0000$threadId\u0000${subAgentId.orEmpty()}"
}

private const val PENDING_CLOSE_CONFIRMATION_RECHECK_INTERVAL_MS = 50L
private const val PENDING_CLOSE_CONFIRMATION_RECHECK_COUNT = 10
