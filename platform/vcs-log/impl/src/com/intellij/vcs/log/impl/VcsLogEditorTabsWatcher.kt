// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow
import com.intellij.vcs.log.visible.VisiblePackRefresher

internal class VcsLogEditorTabsWatcher(private val project: Project,
                                       parentDisposable: Disposable) : VcsLogTabsWatcherExtension<VcsLogEditorTabsWatcher.VcsLogEditorTab> {
  private var tabSelectedCallback: (String) -> Unit = {}

  init {
    val connection = project.messageBus.connect(parentDisposable)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileManagerListener())
  }

  override fun setTabSelectedCallback(callback: (String) -> Unit) {
    tabSelectedCallback = callback
  }

  override fun createLogTab(logId: String,
                            refresher: VisiblePackRefresher,
                            isClosedOnDispose: Boolean): VcsLogEditorTab {
    return VcsLogEditorTab(logId, refresher, isClosedOnDispose)
  }

  override fun isOwnerOf(tab: VcsLogWindow): Boolean {
    return tab is VcsLogEditorTab
  }

  override fun closeTabs(tabs: List<VcsLogWindow>) {
    val editorTabs = tabs.filterIsInstance(VcsLogEditorTab::class.java).filter { it.isClosedOnDispose }.map { it.id }
    val closed = closeLogTabs(project, editorTabs)
    LOG.assertTrue(closed, "Could not close tabs: $editorTabs")
  }

  inner class VcsLogEditorTab(id: String, refresher: VisiblePackRefresher, val isClosedOnDispose: Boolean) : VcsLogWindow(id, refresher) {
    override fun isVisible(): Boolean {
      return getSelectedEditorTabIds(project).contains(id)
    }
  }

  private inner class MyFileManagerListener : FileEditorManagerListener {
    override fun selectionChanged(e: FileEditorManagerEvent) {
      e.newEditor?.let { editor ->
        for (tabId in getLogIds(editor)) {
          tabSelectedCallback(tabId)
        }
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogEditorTabsWatcher::class.java)

    private fun getSelectedEditorTabIds(project: Project): Set<String> {
      return findSelectedLogIds(project)
    }
  }
}