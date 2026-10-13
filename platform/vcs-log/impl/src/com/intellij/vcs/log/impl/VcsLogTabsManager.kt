// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.VcsLogContentUtil.getToolWindow
import com.intellij.vcs.log.impl.VcsLogContentUtil.openLogTab
import com.intellij.vcs.log.impl.VcsLogContentUtil.updateLogUiName
import com.intellij.vcs.log.impl.VcsLogEditorUtil.findVcsLogUi
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.editor.DefaultVcsLogFile
import com.intellij.vcs.log.ui.editor.VcsLogVirtualFileSystem
import java.util.concurrent.CompletableFuture

internal class VcsLogTabsManager(
  private val project: Project,
  private val uiProperties: VcsLogProjectTabsProperties,
  private val logManager: VcsLogManager,
) : Disposable {
  private val futureToolWindow: CompletableFuture<ToolWindow> = CompletableFuture()
  private var filesListenerInstalled = false
  private var toolWindowListenerInstalled = false
  private var isDisposed: Boolean = false

  @RequiresEdt
  fun createTabs() {
    val savedTabs = uiProperties.tabs
    if (savedTabs.isEmpty()) return

    val editorTabs = mutableListOf<String>()
    val toolWindowTabs = mutableListOf<String>()
    for ((id, location) in savedTabs) {
      when (location) {
        VcsLogTabLocation.EDITOR -> editorTabs.add(id)
        VcsLogTabLocation.TOOL_WINDOW -> toolWindowTabs.add(id)
        else -> LOG.warn("Reopening standalone tabs is not supported")
      }
    }
    closeTabsWithoutFilters(editorTabs)
    closeTabsWithoutFilters(toolWindowTabs)

    if (editorTabs.isNotEmpty()) {
      invokeLater(ModalityState.nonModal()) {
        if (logManager.isDisposed) return@invokeLater
        LOG.debug("Reopening editor tabs with ids: $editorTabs")
        editorTabs.forEach { openEditorLogTab(it, false, null) }
      }
    }

    if (toolWindowTabs.isNotEmpty()) {
      futureToolWindow.thenAccept { toolWindow ->
        if (!LOG.assertTrue(!logManager.isDisposed, "Attempting to open tabs on disposed VcsLogManager")) return@thenAccept
        LOG.debug("Reopening toolwindow tabs with ids: $toolWindowTabs")
        toolWindowTabs.forEach { openToolWindowLogTab(toolWindow, it, false, null) }
      }
    }

    ToolWindowManager.getInstance(project).invokeLater {
      if (logManager.isDisposed) return@invokeLater

      val toolWindow = getToolWindow(project) ?: run {
        LOG.error("Could not find tool window by id ${ChangesViewContentManager.TOOLWINDOW_ID}")
        return@invokeLater
      }

      if (toolWindow.isVisible) {
        futureToolWindow.complete(toolWindow)
      }
    }
  }

  private fun closeTabsWithoutFilters(tabs: MutableList<String>) {
    val shouldRestoreWithoutFilters = Registry.`is`("vcs.log.tabs.restore.without.filters", false)
    if (shouldRestoreWithoutFilters) return

    val iter = tabs.iterator()
    // restore one tab without filters for better UX (don't make the user create a new empty tab themselves)
    var oneTabPreserved = false
    while (iter.hasNext()) {
      val tabId = iter.next()
      if (uiProperties.checkTabHasFilters(tabId)) {
        continue
      }
      else if (!oneTabPreserved) {
        oneTabPreserved = true
      }
      else {
        iter.remove()
        uiProperties.removeTab(tabId)
      }
    }
  }

  override fun dispose() {
    isDisposed = true
  }

  @RequiresEdt
  fun toolWindowShown(toolWindow: ToolWindow) {
    futureToolWindow.complete(toolWindow)
  }

  @RequiresEdt
  fun openAnotherLogTab(filters: VcsLogFilterCollection, location: VcsLogTabLocation): MainVcsLogUi {
    require(!isDisposed) { "Already disposed" }
    val tabId = logManager.generateNewLogId()
    uiProperties.resetState(tabId)
    if (location === VcsLogTabLocation.EDITOR) {
      val editors = openEditorLogTab(tabId, true, filters)
      return findVcsLogUi(editors, MainVcsLogUi::class.java)!!
    }
    else if (location === VcsLogTabLocation.TOOL_WINDOW) {
      val toolWindow = VcsLogContentUtil.getToolWindowOrThrow(project)
      futureToolWindow.complete(toolWindow)
      return openToolWindowLogTab(toolWindow, tabId, true, filters)
    }
    throw UnsupportedOperationException("Only log in editor or tool window is supported")
  }

  private fun openEditorLogTab(tabId: String, focus: Boolean, filters: VcsLogFilterCollection?): Array<FileEditor> {
    val file = VcsLogVirtualFileSystem.Holder.getInstance().createVcsLogFile(project, tabId, filters)
    installFilesListener()
    uiProperties.addTab(tabId, VcsLogTabLocation.EDITOR)
    return FileEditorManager.getInstance(project).openFile(file, focus, true)
  }

  private fun installFilesListener() {
    if(filesListenerInstalled) return
    project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file !is DefaultVcsLogFile) return
        // restore the tab after project/log is recreated
        if (this@VcsLogTabsManager.project.isDisposed) return
        uiProperties.removeTab(file.tabId)
      }
    })
    filesListenerInstalled = true
  }

  private fun openToolWindowLogTab(toolWindow: ToolWindow, tabId: String, focus: Boolean,
                                   filters: VcsLogFilterCollection?): MainVcsLogUi {
    val ui = logManager.createLogUi(tabId, filters)
    openLogTab(logManager, toolWindow, VcsLogContentUtil.DEFAULT_TAB_GROUP_ID, ui, { it: MainVcsLogUi -> VcsLogTabsUtil.generateShortDisplayName(it) }, focus)
    ui.onDisplayNameChange { updateLogUiName(project, ui) }

    installContentListener(toolWindow)
    uiProperties.addTab(tabId, VcsLogTabLocation.TOOL_WINDOW)

    return ui
  }

  private fun installContentListener(toolWindow: ToolWindow) {
    if (toolWindowListenerInstalled) return
    val listener = object : ContentManagerListener {
      override fun contentRemoved(event: ContentManagerEvent) {
        // restore the tab after project/log is recreated
        if (this@VcsLogTabsManager.project.isDisposed) return
        val id = VcsLogContentUtil.getId(event.content)
        if (id != null) {
          uiProperties.removeTab(id)
        }
      }
    }
    toolWindow.addContentManagerListener(listener)
    Disposer.register(this) {
      if (!toolWindow.isDisposed) {
        toolWindow.contentManagerIfCreated?.removeContentManagerListener(listener)
      }
    }
    toolWindowListenerInstalled = true
  }

  fun getTabs(): Set<String> = uiProperties.tabs.keys

  fun getTabs(location: VcsLogTabLocation): Set<String> =
    uiProperties.tabs.filterValues { it === location }.keys

  companion object {
    private val LOG = Logger.getInstance(VcsLogTabsManager::class.java)

    fun MainVcsLogUi.onDisplayNameChange(block: () -> Unit) {
      filterUi.addFilterListener { block() }
      properties.onPropertyChange(this) {
        if (it == MainVcsLogUiProperties.GRAPH_OPTIONS) block()
      }
    }
  }
}
