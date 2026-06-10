// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
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
class AgentChatPendingEditorLifecycleService(
  project: Project,
  private val serviceScope: CoroutineScope,
) : Disposable {
  private val pendingCloseJobs = ConcurrentHashMap<String, Job>()

  init {
    project.messageBus.connect(this).apply {
      subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
          override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            val chatFile = file as? AgentChatVirtualFile ?: return
            if (!participatesInPendingAgentChatProjection(chatFile)) {
              return
            }
            if (source.isFileOpen(chatFile) || isOpenInAnyProject(chatFile)) {
              publishOpenPendingTabsChanged(chatFile)
              return
            }
            if (chatFile.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) == true) {
              schedulePendingCloseConfirmation(chatFile)
              return
            }
            publishOpenPendingTabsChanged(chatFile)
          }
        }
      )
      subscribe(
        FileOpenedSyncListener.TOPIC,
        object : FileOpenedSyncListener {
          override fun fileOpenedSync(
            source: FileEditorManager,
            file: VirtualFile,
            editorsWithProviders: List<FileEditorWithProvider>,
          ) {
            val chatFile = file as? AgentChatVirtualFile ?: return
            if (!participatesInPendingAgentChatProjection(chatFile)) {
              return
            }
            cancelPendingCloseJob(chatFile.tabKey)
            publishOpenPendingTabsChanged(chatFile)
          }
        }
      )
    }
  }

  override fun dispose() {
    pendingCloseJobs.values.forEach { job -> job.cancel() }
    pendingCloseJobs.clear()
  }

  private fun schedulePendingCloseConfirmation(file: AgentChatVirtualFile) {
    cancelPendingCloseJob(file.tabKey)
    val job = serviceScope.launch(Dispatchers.UI) {
      repeat(PENDING_CLOSE_CONFIRMATION_RECHECK_COUNT) {
        delay(PENDING_CLOSE_CONFIRMATION_RECHECK_INTERVAL_MS.milliseconds)
        if (isOpenInAnyProject(file)) {
          return@launch
        }
      }
      pendingCloseJobs.remove(file.tabKey)
      if (!isOpenInAnyProject(file)) {
        publishOpenPendingTabsChanged(file)
      }
    }
    pendingCloseJobs[file.tabKey] = job
    job.invokeOnCompletion {
      pendingCloseJobs.remove(file.tabKey, job)
    }
  }

  private fun cancelPendingCloseJob(tabKey: String) {
    pendingCloseJobs.remove(tabKey)?.cancel()
  }

  private fun publishOpenPendingTabsChanged(file: AgentChatVirtualFile) {
    service<AgentChatOpenPendingTabsStateService>().refreshOpenTabs()
    notifyAgentChatScopedRefresh(provider = file.provider ?: return, projectPath = file.projectPath)
  }
}

private fun isOpenInAnyProject(file: VirtualFile): Boolean {
  return ProjectManager.getInstance().openProjects.any { openProject ->
    !openProject.isDisposed && FileEditorManager.getInstance(openProject).isFileOpen(file)
  }
}

private const val PENDING_CLOSE_CONFIRMATION_RECHECK_INTERVAL_MS = 50L
private const val PENDING_CLOSE_CONFIRMATION_RECHECK_COUNT = 10
