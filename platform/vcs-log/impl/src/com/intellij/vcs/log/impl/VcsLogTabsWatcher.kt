// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.TabbedContent
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow
import com.intellij.vcs.log.impl.VcsLogManager.LogWindowKind
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.visible.VisiblePackRefresher
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*

internal class VcsLogTabsWatcher(private val project: Project, private val postponableLogRefresher: PostponableLogRefresher) : Disposable {
  private val toolwindowListenerDisposable = Disposer.newDisposable()

  private val toolWindow: ToolWindow? get() = getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)

  init {
    val connection = project.messageBus.connect(this)
    connection.subscribe(ToolWindowManagerListener.TOPIC, MyToolWindowManagerListener())
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileManagerListener())
    installContentListeners()
  }


  fun addTabToWatch(logId: String, refresher: VisiblePackRefresher,
                    kind: LogWindowKind, isClosedOnDispose: Boolean): Disposable {
    val window = when (kind) {
      LogWindowKind.TOOL_WINDOW -> VcsLogToolWindowTab(logId, refresher, isClosedOnDispose)
      LogWindowKind.EDITOR -> VcsLogEditorTab(logId, refresher, isClosedOnDispose)
      else -> VcsLogWindow(logId, refresher)
    }
    return postponableLogRefresher.addLogWindow(window)
  }

  private fun installContentListeners() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindow?.let { window ->
      addContentManagerListener(window, MyRefreshPostponedEventsListener(window), toolwindowListenerDisposable)
    }
  }

  private fun removeContentListeners() {
    Disposer.dispose(toolwindowListenerDisposable)
  }

  private fun selectionChanged(tabId: String) {
    val logWindow = postponableLogRefresher.logWindows.find { window -> window.id == tabId }
    if (logWindow != null) {
      LOG.debug("Selected log window '$logWindow'")
      VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.TAB_NAVIGATED, null, project)
      postponableLogRefresher.refresherActivated(logWindow.refresher, false)
    }
  }

  private fun closeLogTabs() {
    toolWindow?.let { window ->
      val toolWindowTabs = getToolWindowTabsToClose()
      for (tabId in toolWindowTabs) {
        val closed = VcsLogContentUtil.closeLogTab(window.contentManager, tabId)
        LOG.assertTrue(closed, """
   Could not find content component for tab $tabId
   Existing content: ${Arrays.toString(window.contentManager.contents)}
   Tabs to close: $toolWindowTabs
   """.trimIndent())
      }
    }
    val editorTabs = getEditorTabsToClose()
    val closed = closeLogTabs(project, editorTabs)
    LOG.assertTrue(closed, "Could not close tabs: $editorTabs")
  }

  private fun getToolWindowTabsToClose(): List<String> {
    return postponableLogRefresher.logWindows.filterIsInstance(VcsLogToolWindowTab::class.java).filter {
      it.isClosedOnDispose
    }.map { it.id }
  }

  private fun getEditorTabsToClose(): List<String> {
    return postponableLogRefresher.logWindows.filterIsInstance(VcsLogEditorTab::class.java).filter {
      it.isClosedOnDispose
    }.map { it.id }
  }

  override fun dispose() {
    closeLogTabs()
    removeContentListeners()
  }

  private inner class VcsLogToolWindowTab(id: String, refresher: VisiblePackRefresher,
                                          val isClosedOnDispose: Boolean) : VcsLogWindow(id, refresher) {
    override fun isVisible(): Boolean {
      return id == getSelectedToolWindowTabId(toolWindow)
    }
  }

  private inner class VcsLogEditorTab(id: String, refresher: VisiblePackRefresher,
                                      val isClosedOnDispose: Boolean) : VcsLogWindow(id, refresher) {
    override fun isVisible(): Boolean {
      return findSelectedLogIds(project).contains(id)
    }
  }

  private inner class MyToolWindowManagerListener : ToolWindowManagerListener {
    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
      if (ids.contains(ChangesViewContentManager.TOOLWINDOW_ID)) {
        installContentListeners()
      }
    }

    override fun toolWindowUnregistered(id: String, toolWindow: ToolWindow) {
      if (id == ChangesViewContentManager.TOOLWINDOW_ID) {
        removeContentListeners()
      }
    }
  }

  private inner class MyFileManagerListener : FileEditorManagerListener {
    override fun selectionChanged(e: FileEditorManagerEvent) {
      e.newEditor?.let { editor ->
        for (tabId in getLogIds(editor)) {
          this@VcsLogTabsWatcher.selectionChanged(tabId)
        }
      }
    }
  }

  private inner class MyRefreshPostponedEventsListener(toolWindow: ToolWindow)
    : VcsLogTabsListener(project, toolWindow, toolwindowListenerDisposable) {
    override fun selectionChanged(tabId: String) {
      this@VcsLogTabsWatcher.selectionChanged(tabId)
    }
  }

  private abstract class VcsLogTabsListener(project: Project, private val window: ToolWindow, disposable: Disposable) :
    ToolWindowManagerListener, PropertyChangeListener, ContentManagerListener {

    init {
      project.messageBus.connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, this)
      Disposer.register(disposable) {
        val contentManager = window.contentManagerIfCreated ?: return@register
        for (content in contentManager.contents) {
          (content as? TabbedContent)?.removePropertyChangeListener(this)
        }
      }
    }

    protected abstract fun selectionChanged(tabId: String)

    private fun selectionChanged() {
      getSelectedToolWindowTabId(window)?.let { selectionChanged(it) }
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      if (ContentManagerEvent.ContentOperation.add == event.operation) {
        VcsLogContentUtil.getId(event.content)?.let { selectionChanged(it) }
      }
    }

    override fun contentAdded(event: ContentManagerEvent) {
      (event.content as? TabbedContent)?.addPropertyChangeListener(this)
    }

    override fun contentRemoved(event: ContentManagerEvent) {
      (event.content as? TabbedContent)?.removePropertyChangeListener(this)
    }

    override fun toolWindowShown(toolWindow: ToolWindow) {
      if (window === toolWindow) selectionChanged()
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
      if (evt.propertyName == Content.PROP_COMPONENT) {
        selectionChanged()
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogTabsWatcher::class.java)

    private fun getSelectedToolWindowTabId(toolWindow: ToolWindow?): String? {
      if (toolWindow == null || !toolWindow.isVisible) {
        return null
      }
      val content = toolWindow.contentManager.selectedContent ?: return null
      return VcsLogContentUtil.getId(content)
    }

    private fun addContentManagerListener(window: ToolWindow,
                                          listener: ContentManagerListener,
                                          disposable: Disposable) {
      window.addContentManagerListener(listener)
      Disposer.register(disposable) {
        if (!window.isDisposed) {
          val contentManager = window.contentManagerIfCreated
          contentManager?.removeContentManagerListener(listener)
        }
      }
    }
  }
}