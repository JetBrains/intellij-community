// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentChatLiveTerminalRegistryService>()

/**
 * Keeps the live terminal session bound to the logical chat tab identified by [AgentChatVirtualFile.tabKey].
 *
 * Editor widgets may be disposed and recreated by the IDE during drag-and-drop tab moves, split changes, or
 * detach/reattach flows. Those UI transitions must not restart or interrupt the underlying agent session.
 */
internal interface AgentChatLiveTerminalRegistry {
  /**
   * Returns the existing live terminal for [file], or creates it on first attachment.
   */
  fun acquireOrCreate(
    file: AgentChatVirtualFile,
    terminalTabs: AgentChatTerminalTabs,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentChatTerminalTab
}

/**
 * Project-scoped implementation that owns cleanup for retained chat terminals.
 *
 * Terminals are released only when the last open copy of the chat file closes, or when the project itself is disposed.
 */
@Service(Service.Level.PROJECT)
internal class AgentChatLiveTerminalRegistryService(
  private val project: Project,
  private val serviceScope: CoroutineScope,
) : AgentChatLiveTerminalRegistry, Disposable {
  private val store = AgentChatLiveTerminalApplicationStore.store
  private val pendingCloseJobs = ConcurrentHashMap<String, Job>()

  init {
    project.messageBus.connect(this).apply {
      subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
          override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            val chatFile = file as? AgentChatVirtualFile ?: return
            when (store.handleFileClosed(project = project, source = source, file = chatFile)) {
              AgentChatLiveTerminalCloseResult.DEFERRED -> schedulePendingCloseConfirmation(source = source, file = chatFile)
              AgentChatLiveTerminalCloseResult.KEPT_OPEN -> cancelPendingCloseJob(chatFile.tabKey)
              AgentChatLiveTerminalCloseResult.CLOSED -> {
                cancelPendingCloseJob(chatFile.tabKey)
                archiveClosedTerminalSession(chatFile)
              }
            }
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
            cancelPendingCloseJob(chatFile.tabKey)
            store.handleFileOpened(chatFile)
          }
        }
      )
    }
  }

  override fun acquireOrCreate(
    file: AgentChatVirtualFile,
    terminalTabs: AgentChatTerminalTabs,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentChatTerminalTab {
    cancelPendingCloseJob(file.tabKey)
    return store.acquireOrCreate(project = project, file = file, terminalTabs = terminalTabs, startupLaunchSpec = startupLaunchSpec)
  }

  override fun dispose() {
    pendingCloseJobs.values.forEach(Job::cancel)
    pendingCloseJobs.clear()
    store.disposeProject(project)
  }

  private fun schedulePendingCloseConfirmation(source: FileEditorManager, file: AgentChatVirtualFile) {
    val tabKey = file.tabKey
    val job = serviceScope.launch(start = CoroutineStart.LAZY) {
      repeat(PENDING_CLOSE_CONFIRMATION_RECHECK_COUNT) {
        delay(PENDING_CLOSE_CONFIRMATION_RECHECK_INTERVAL_MS.milliseconds)
        val reopened = withContext(Dispatchers.EDT) {
          !project.isDisposed && source.isFileOpen(file)
        }
        if (reopened) {
          withContext(Dispatchers.EDT) {
            store.handleFileOpened(file)
          }
          return@launch
        }
      }

      withContext(Dispatchers.EDT) {
        if (!project.isDisposed) {
          if (store.confirmPendingClose(project = project, source = source, file = file) == AgentChatLiveTerminalCloseResult.CLOSED) {
            archiveClosedTerminalSession(file)
          }
        }
      }
    }
    registerPendingCloseJob(tabKey = tabKey, job = job)
    job.start()
  }

  private fun registerPendingCloseJob(tabKey: String, job: Job) {
    pendingCloseJobs.put(tabKey, job)?.cancel()
    job.invokeOnCompletion {
      pendingCloseJobs.remove(tabKey, job)
    }
  }

  private fun cancelPendingCloseJob(tabKey: String) {
    pendingCloseJobs.remove(tabKey)?.cancel()
  }

  private fun archiveClosedTerminalSession(file: AgentChatVirtualFile) {
    if (!shouldArchiveTerminalSessionOnLastEditorClose(file)) {
      return
    }
    serviceScope.launch {
      val descriptor = AgentSessionProviders.find(AgentSessionProvider.TERMINAL)
      if (descriptor == null || !descriptor.supportsArchiveThread) {
        return@launch
      }
      try {
        descriptor.archiveThread(path = file.projectPath, threadId = file.threadId)
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        LOG.warn("Failed to archive closed terminal session ${file.threadId}", t)
      }
    }
  }
}

internal fun shouldArchiveTerminalSessionOnLastEditorClose(file: AgentChatVirtualFile): Boolean {
  return file.provider == AgentSessionProvider.TERMINAL &&
         !file.isPendingThread &&
         file.subAgentId == null &&
         file.projectPath.isNotBlank() &&
         file.threadId.isNotBlank()
}

private object AgentChatLiveTerminalApplicationStore {
  val store: AgentChatLiveTerminalStore = AgentChatLiveTerminalStore(::findOpenProjectForFile)

  private fun findOpenProjectForFile(file: AgentChatVirtualFile, excludedProject: Project?): Project? {
    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed || project == excludedProject) {
        continue
      }
      val manager = project.serviceIfCreated<FileEditorManager>() ?: continue
      if (manager.isFileOpen(file)) {
        return project
      }
    }
    return null
  }
}

internal enum class AgentChatLiveTerminalCloseResult {
  KEPT_OPEN,
  DEFERRED,
  CLOSED,
}

/**
 * Synchronized in-memory store used by the project service and lightweight lifecycle tests.
 */
internal class AgentChatLiveTerminalStore(
  private val findOpenProjectForFile: (AgentChatVirtualFile, Project?) -> Project? = { _, _ -> null },
) {
  private data class LiveTerminalEntry(
    var project: Project,
    var file: AgentChatVirtualFile,
    val tab: AgentChatTerminalTab,
    val terminalTabs: AgentChatTerminalTabs,
  )

  private val entries = LinkedHashMap<String, LiveTerminalEntry>()
  private val pendingCloseTabKeys = LinkedHashSet<String>()

  /**
   * Reuses the retained terminal for the same logical tab, preserving the running session across editor recreation.
   */
  @Synchronized
  fun acquireOrCreate(
    project: Project,
    file: AgentChatVirtualFile,
    terminalTabs: AgentChatTerminalTabs,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec = AgentSessionTerminalLaunchSpec(command = emptyList()),
  ): AgentChatTerminalTab {
    pendingCloseTabKeys.remove(file.tabKey)
    val existing = entries.get(file.tabKey)
    if (existing != null) {
      existing.project = project
      existing.file = file
      return existing.tab
    }

    val createdTab = terminalTabs.createTab(project, file, startupLaunchSpec)
    entries.put(file.tabKey, LiveTerminalEntry(project = project, file = file, tab = createdTab, terminalTabs = terminalTabs))
    return createdTab
  }

  /**
   * Closes the retained terminal only after the IDE reports that no copy of [file] remains open.
   */
  @Synchronized
  fun handleFileClosed(
    project: Project,
    source: FileEditorManager,
    file: AgentChatVirtualFile,
  ): AgentChatLiveTerminalCloseResult {
    if (source.isFileOpen(file)) {
      retainOpenEntry(tabKey = file.tabKey, project = project)
      return AgentChatLiveTerminalCloseResult.KEPT_OPEN
    }
    val openProject = findOpenProjectForFile(file, project)
    if (openProject != null) {
      retainOpenEntry(tabKey = file.tabKey, project = openProject)
      return AgentChatLiveTerminalCloseResult.KEPT_OPEN
    }

    if (file.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) == true) {
      pendingCloseTabKeys.add(file.tabKey)
      return AgentChatLiveTerminalCloseResult.DEFERRED
    }

    pendingCloseTabKeys.remove(file.tabKey)
    return closeAndRemove(tabKey = file.tabKey)
  }

  @Synchronized
  fun handleFileOpened(file: AgentChatVirtualFile) {
    pendingCloseTabKeys.remove(file.tabKey)
  }

  @Synchronized
  fun confirmPendingClose(
    project: Project,
    source: FileEditorManager,
    file: AgentChatVirtualFile,
  ): AgentChatLiveTerminalCloseResult {
    if (!pendingCloseTabKeys.remove(file.tabKey)) {
      return AgentChatLiveTerminalCloseResult.KEPT_OPEN
    }
    if (source.isFileOpen(file)) {
      retainOpenEntry(tabKey = file.tabKey, project = project)
      return AgentChatLiveTerminalCloseResult.KEPT_OPEN
    }
    val openProject = findOpenProjectForFile(file, project)
    if (openProject != null) {
      retainOpenEntry(tabKey = file.tabKey, project = openProject)
      return AgentChatLiveTerminalCloseResult.KEPT_OPEN
    }
    return closeAndRemove(tabKey = file.tabKey)
  }

  /**
   * Releases every retained terminal during project shutdown.
   */
  @Suppress("UNUSED_PARAMETER")
  @Synchronized
  fun dispose(project: Project) {
    val entriesToClose = entries.values.toList()
    entries.clear()
    pendingCloseTabKeys.clear()
    for ((entryProject, file, tab, terminalTabs) in entriesToClose) {
      terminalTabs.closeTab(entryProject, tab)
      recordTerminalSessionClosed(file)
    }
  }

  @Synchronized
  fun disposeProject(project: Project) {
    val entriesToProcess = entries.entries
      .filter { entry -> entry.value.project == project }
      .map { entry -> entry.key to entry.value }
    val entriesToClose = mutableListOf<LiveTerminalEntry>()
    for ((tabKey, entry) in entriesToProcess) {
      val openProject = findOpenProjectForFile(entry.file, project)
      if (openProject != null) {
        entry.project = openProject
        pendingCloseTabKeys.remove(tabKey)
      }
      else {
        entries.remove(tabKey)
        pendingCloseTabKeys.remove(tabKey)
        entriesToClose.add(entry)
      }
    }
    for ((entryProject, file, tab, terminalTabs) in entriesToClose) {
      terminalTabs.closeTab(entryProject, tab)
      recordTerminalSessionClosed(file)
    }
  }

  @TestOnly
  @Synchronized
  fun isTracked(tabKey: String): Boolean {
    return entries.containsKey(tabKey)
  }

  @TestOnly
  @Synchronized
  fun isPendingClose(tabKey: String): Boolean {
    return pendingCloseTabKeys.contains(tabKey)
  }

  private fun closeAndRemove(tabKey: String): AgentChatLiveTerminalCloseResult {
    val entry = entries.remove(tabKey) ?: return AgentChatLiveTerminalCloseResult.KEPT_OPEN
    entry.terminalTabs.closeTab(entry.project, entry.tab)
    recordTerminalSessionClosed(entry.file)
    return AgentChatLiveTerminalCloseResult.CLOSED
  }

  private fun recordTerminalSessionClosed(file: AgentChatVirtualFile) {
    val provider = file.provider ?: return
    val projectPath = file.projectPath.takeIf { it.isNotBlank() } ?: return
    val threadId = file.threadId.takeIf { it.isNotBlank() } ?: return
    val descriptor = AgentSessionProviders.find(provider) ?: return
    try {
      descriptor.recordTerminalSessionClosed(path = projectPath, threadId = threadId)
    }
    catch (t: Throwable) {
      LOG.warn("Failed to record closed terminal session ${file.threadId}", t)
    }
  }

  private fun retainOpenEntry(tabKey: String, project: Project) {
    pendingCloseTabKeys.remove(tabKey)
    entries.get(tabKey)?.project = project
  }
}

private const val PENDING_CLOSE_CONFIRMATION_RECHECK_INTERVAL_MS = 50L
private const val PENDING_CLOSE_CONFIRMATION_RECHECK_COUNT = 10
