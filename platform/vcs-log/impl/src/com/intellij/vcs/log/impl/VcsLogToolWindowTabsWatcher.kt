// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.TabbedContent
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow
import com.intellij.vcs.log.impl.VcsLogToolWindowTabsWatcher.VcsLogToolWindowTab
import com.intellij.vcs.log.ui.VcsLogUiEx
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*

internal class VcsLogToolWindowTabsWatcher(private val project: Project,
                                           private val toolWindowId: String,
                                           parentDisposable: Disposable) : VcsLogTabsWatcherExtension<VcsLogToolWindowTab> {
  private val mainDisposable = Disposer.newDisposable()
  private val toolwindowListenerDisposable = Disposer.newDisposable()
  private var tabSelectedCallback: (String) -> Unit = {}

  private val toolWindow: ToolWindow?
    get() = getInstance(project).getToolWindow(toolWindowId)

  init {
    val connection = project.messageBus.connect(mainDisposable)
    connection.subscribe(ToolWindowManagerListener.TOPIC, MyToolWindowManagerListener())
    installContentListeners()
    Disposer.register(parentDisposable, mainDisposable)
    Disposer.register(parentDisposable, toolwindowListenerDisposable)
  }

  override fun setTabSelectedCallback(callback: (String) -> Unit) {
    tabSelectedCallback = callback
  }

  override fun createLogTab(ui: VcsLogUiEx, isClosedOnDispose: Boolean): VcsLogToolWindowTab {
    return VcsLogToolWindowTab(ui, isClosedOnDispose)
  }

  override fun isOwnerOf(tab: VcsLogWindow): Boolean {
    return tab is VcsLogToolWindowTab
  }

  override fun closeTabs(tabs: List<VcsLogWindow>) {
    toolWindow?.let { window ->
      val tabIds = tabs.filterIsInstance(VcsLogToolWindowTab::class.java).filter { it.isClosedOnDispose }.map { it.id }
      for (tabId in tabIds) {
        val closed = VcsLogContentUtil.closeLogTab(window.contentManager, tabId)
        LOG.assertTrue(closed, """
           Could not find content component for tab ${tabId}
           Existing content: ${Arrays.toString(window.contentManager.contents)}
           Tabs to close: $tabIds
           """.trimIndent())
      }
    }
  }

  private fun installContentListeners() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindow?.let { window ->
      addContentManagerListener(window, object : VcsLogTabsListener(project, window, mainDisposable) {
        override fun selectionChanged(tabId: String) {
          tabSelectedCallback(tabId)
        }
      }, toolwindowListenerDisposable)
    }
  }

  private fun removeContentListeners() {
    Disposer.dispose(toolwindowListenerDisposable)
  }

  inner class VcsLogToolWindowTab(ui: VcsLogUiEx, val isClosedOnDispose: Boolean) : VcsLogWindow(ui) {
    override fun isVisible(): Boolean {
      val selectedTab = getSelectedToolWindowTabId(toolWindow)
      return id == selectedTab
    }

    override fun toString(): @NonNls String {
      return "VcsLogToolWindowTab '$id'"
    }
  }

  private inner class MyToolWindowManagerListener : ToolWindowManagerListener {
    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
      if (ids.contains(toolWindowId)) {
        installContentListeners()
      }
    }

    override fun toolWindowUnregistered(id: String, toolWindow: ToolWindow) {
      if (id == toolWindowId) {
        removeContentListeners()
      }
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
        val tabId = VcsLogContentUtil.getId(event.content)
        tabId?.let { selectionChanged(it) }
      }
    }

    override fun contentAdded(event: ContentManagerEvent) {
      val content = event.content
      (content as? TabbedContent)?.addPropertyChangeListener(this)
    }

    override fun contentRemoved(event: ContentManagerEvent) {
      val content = event.content
      (content as? TabbedContent)?.removePropertyChangeListener(this)
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
    private val LOG = Logger.getInstance(VcsLogToolWindowTabsWatcher::class.java)

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
          window.contentManagerIfCreated?.removeContentManagerListener(listener)
        }
      }
    }
  }
}