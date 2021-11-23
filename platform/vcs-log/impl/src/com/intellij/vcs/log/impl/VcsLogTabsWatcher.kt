// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow
import com.intellij.vcs.log.impl.VcsLogManager.LogWindowKind
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.visible.VisiblePackRefresher
import java.util.function.Consumer

internal class VcsLogTabsWatcher(private val project: Project, private val postponableLogRefresher: PostponableLogRefresher) : Disposable {
  private val extensions = mapOf(
    Pair(LogWindowKind.TOOL_WINDOW, VcsLogToolWindowTabsWatcher(project, ChangesViewContentManager.TOOLWINDOW_ID, this)),
    Pair(LogWindowKind.EDITOR, VcsLogEditorTabsWatcher(project, this))
  )

  init {
    extensions.values.forEach(Consumer { extension ->
      extension.setTabSelectedCallback { tabId -> selectionChanged(tabId) }
    })
  }

  fun addTabToWatch(logId: String, refresher: VisiblePackRefresher,
                    kind: LogWindowKind, isClosedOnDispose: Boolean): Disposable {
    val extension = extensions[kind]
    val window = extension?.createLogTab(logId, refresher, isClosedOnDispose) ?: VcsLogWindow(logId, refresher)
    return postponableLogRefresher.addLogWindow(window)
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
  fun createLogTab(logId: String, refresher: VisiblePackRefresher, isClosedOnDispose: Boolean): T
  fun isOwnerOf(tab: VcsLogWindow): Boolean
  fun closeTabs(tabs: List<VcsLogWindow>)
}