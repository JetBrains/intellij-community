// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogUiEx
import java.util.function.Consumer

internal class VcsLogTabsWatcher(private val project: Project, private val postponableLogRefresher: PostponableLogRefresher) : Disposable {
  private val extensions = mapOf(
    Pair(VcsLogTabLocation.TOOL_WINDOW, VcsLogToolWindowTabsWatcher(project, ChangesViewContentManager.TOOLWINDOW_ID, this)),
    Pair(VcsLogTabLocation.EDITOR, VcsLogEditorTabsWatcher(project, this))
  )

  init {
    extensions.values.forEach(Consumer { extension ->
      extension.setTabSelectedCallback { tabId -> selectionChanged(tabId) }
    })
  }

  fun addTabToWatch(ui: VcsLogUiEx, location: VcsLogTabLocation, isClosedOnDispose: Boolean): Disposable {
    val extension = extensions[location]
    val window = extension?.createLogTab(ui, isClosedOnDispose) ?: VcsLogWindow(ui)
    return postponableLogRefresher.addLogWindow(window)
  }

  fun getTabs(): List<VcsLogUiEx> {
    return postponableLogRefresher.logWindows.map { it.ui }
  }

  private fun getLogWindows(location: VcsLogTabLocation): List<VcsLogWindow> {
    val watcherExtension = extensions[location]
    if (watcherExtension == null) {
      return postponableLogRefresher.logWindows.filter { tab -> extensions.values.none { it.isOwnerOf(tab) } }
    }
    return postponableLogRefresher.logWindows.filter(watcherExtension::isOwnerOf)
  }

  fun getTabs(location: VcsLogTabLocation): List<VcsLogUiEx> {
    return getLogWindows(location).map { it.ui }
  }

  fun getVisibleTabs(location: VcsLogTabLocation): List<VcsLogUiEx> {
    return getLogWindows(location).filter { it.isVisible }.map { it.ui }
  }

  private fun selectionChanged(tabId: String) {
    val logWindow = postponableLogRefresher.logWindows.find { window -> window.id == tabId }
    if (logWindow != null) {
      LOG.debug("Selected log window '$logWindow'")
      VcsLogUsageTriggerCollector.triggerTabNavigated(project)
      postponableLogRefresher.refresherActivated(logWindow.refresher, false)
    }
  }

  private fun closeLogTabs() {
    for (extension in extensions.values) {
      extension.closeTabs(postponableLogRefresher.logWindows.filter(extension::isOwnerOf))
    }
  }

  override fun dispose() {
    closeLogTabs()
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogTabsWatcher::class.java)
  }
}

internal interface VcsLogTabsWatcherExtension<T : VcsLogWindow> {
  fun setTabSelectedCallback(callback: (String) -> Unit)
  fun createLogTab(ui: VcsLogUiEx, isClosedOnDispose: Boolean): T
  fun isOwnerOf(tab: VcsLogWindow): Boolean
  fun closeTabs(tabs: List<VcsLogWindow>)
}