// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.agent.workbench.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly

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
  fun acquireOrCreate(file: AgentChatVirtualFile, terminalTabs: AgentChatTerminalTabs): AgentChatTerminalTab
}

/**
 * Project-scoped implementation that owns cleanup for retained chat terminals.
 *
 * Terminals are released only when the last open copy of the chat file closes, or when the project itself is disposed.
 */
@Service(Service.Level.PROJECT)
internal class AgentChatLiveTerminalRegistryService(private val project: Project) : AgentChatLiveTerminalRegistry, Disposable {
  private val store = AgentChatLiveTerminalStore()

  init {
    project.messageBus.connect(this).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          val chatFile = file as? AgentChatVirtualFile ?: return
          store.handleFileClosed(project = project, source = source, file = chatFile)
        }
      }
    )
  }

  override fun acquireOrCreate(file: AgentChatVirtualFile, terminalTabs: AgentChatTerminalTabs): AgentChatTerminalTab {
    return store.acquireOrCreate(project = project, file = file, terminalTabs = terminalTabs)
  }

  override fun dispose() {
    store.dispose(project)
  }
}

/**
 * Synchronized in-memory store used by the project service and lightweight lifecycle tests.
 */
internal class AgentChatLiveTerminalStore {
  private data class LiveTerminalEntry(
    val tab: AgentChatTerminalTab,
    val terminalTabs: AgentChatTerminalTabs,
  )

  private val entries = LinkedHashMap<String, LiveTerminalEntry>()

  /**
   * Reuses the retained terminal for the same logical tab, preserving the running session across editor recreation.
   */
  @Synchronized
  fun acquireOrCreate(project: Project, file: AgentChatVirtualFile, terminalTabs: AgentChatTerminalTabs): AgentChatTerminalTab {
    val existing = entries.get(file.tabKey)
    if (existing != null) {
      return existing.tab
    }

    val createdTab = terminalTabs.createTab(project, file)
    entries.put(file.tabKey, LiveTerminalEntry(tab = createdTab, terminalTabs = terminalTabs))
    return createdTab
  }

  /**
   * Closes the retained terminal only after the IDE reports that no copy of [file] remains open.
   */
  @Synchronized
  fun handleFileClosed(project: Project, source: FileEditorManager, file: AgentChatVirtualFile) {
    if (source.isFileOpen(file)) {
      return
    }
    closeAndRemove(project = project, tabKey = file.tabKey)
  }

  /**
   * Releases every retained terminal during project shutdown.
   */
  @Synchronized
  fun dispose(project: Project) {
    val entriesToClose = entries.values.toList()
    entries.clear()
    for (entry in entriesToClose) {
      entry.terminalTabs.closeTab(project, entry.tab)
    }
  }

  @TestOnly
  @Synchronized
  fun isTracked(tabKey: String): Boolean {
    return entries.containsKey(tabKey)
  }

  private fun closeAndRemove(project: Project, tabKey: String) {
    val entry = entries.remove(tabKey) ?: return
    entry.terminalTabs.closeTab(project, entry.tab)
  }
}
