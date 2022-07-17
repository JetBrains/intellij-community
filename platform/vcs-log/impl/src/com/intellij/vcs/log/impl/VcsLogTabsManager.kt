// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.TabGroupId
import com.intellij.util.ContentUtilEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogContentUtil.openLogTab
import com.intellij.vcs.log.impl.VcsLogContentUtil.updateLogUiName
import com.intellij.vcs.log.impl.VcsLogEditorUtil.findVcsLogUi
import com.intellij.vcs.log.impl.VcsLogManager.VcsLogUiFactory
import com.intellij.vcs.log.impl.VcsProjectLog.ProjectLogListener
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.editor.VcsLogVirtualFileSystem
import com.intellij.vcs.log.visible.filters.getPresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*

class VcsLogTabsManager internal constructor(private val project: Project,
                                             private val uiProperties: VcsLogProjectTabsProperties,
                                             parent: Disposable) {
  private var isLogDisposing = false

  // for statistics
  val tabs: Collection<String> get() = uiProperties.tabs.keys

  init {
    project.messageBus.connect(parent).subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, object : ProjectLogListener {
      override fun logCreated(manager: VcsLogManager) {
        isLogDisposing = false
        val savedTabs = uiProperties.tabs
        if (savedTabs.isEmpty()) return

        ToolWindowManager.getInstance(project).invokeLater {
          if (manager !== VcsProjectLog.getInstance(project).logManager) return@invokeLater
          if (LOG.assertTrue(!manager.isDisposed, "Attempting to open tabs on disposed VcsLogManager")) {
            reopenLogTabs(manager, savedTabs)
          }
        }
      }

      override fun logDisposed(manager: VcsLogManager) {
        isLogDisposing = true
      }
    })
  }

  @RequiresEdt
  private fun reopenLogTabs(manager: VcsLogManager, tabs: Map<String, VcsLogTabLocation>) {
    tabs.forEach { (id: String, location: VcsLogTabLocation) ->
      if (location === VcsLogTabLocation.EDITOR) {
        openEditorLogTab(id, false, null)
      }
      else if (location === VcsLogTabLocation.TOOL_WINDOW) {
        openToolWindowLogTab(manager, id, false, null)
      }
      else {
        LOG.warn("Unsupported log tab location $location")
      }
    }
  }

  fun openAnotherLogTab(manager: VcsLogManager, filters: VcsLogFilterCollection,
                        location: VcsLogTabLocation): MainVcsLogUi {
    val tabId = generateTabId(manager)
    uiProperties.resetState(tabId)
    if (location === VcsLogTabLocation.EDITOR) {
      val editors = openEditorLogTab(tabId, true, filters)
      return findVcsLogUi(editors, MainVcsLogUi::class.java)!!
    }
    else if (location === VcsLogTabLocation.TOOL_WINDOW) {
      return openToolWindowLogTab(manager, tabId, true, filters)
    }
    throw UnsupportedOperationException("Only log in editor or tool window is supported")
  }

  private fun openEditorLogTab(tabId: String, focus: Boolean, filters: VcsLogFilterCollection?): Array<FileEditor> {
    val file = VcsLogVirtualFileSystem.getInstance().createVcsLogFile(project, tabId, filters)
    return FileEditorManager.getInstance(project).openFile(file, focus, true)
  }

  private fun openToolWindowLogTab(manager: VcsLogManager, tabId: String, focus: Boolean,
                                   filters: VcsLogFilterCollection?): MainVcsLogUi {
    val factory = getPersistentVcsLogUiFactory(manager, tabId,
                                               VcsLogTabLocation.TOOL_WINDOW,
                                               filters)
    val ui = openLogTab(project, manager, TAB_GROUP_ID, { u: MainVcsLogUi -> generateShortDisplayName(u) }, factory, focus)
    ui.filterUi.addFilterListener { updateLogUiName(project, ui) }
    return ui
  }

  @RequiresEdt
  @ApiStatus.Internal
  fun getPersistentVcsLogUiFactory(manager: VcsLogManager,
                                   tabId: String,
                                   location: VcsLogTabLocation,
                                   filters: VcsLogFilterCollection?): VcsLogUiFactory<MainVcsLogUi> {
    return PersistentVcsLogUiFactory(manager.getMainLogUiFactory(tabId, filters), location)
  }

  private inner class PersistentVcsLogUiFactory constructor(private val factory: VcsLogUiFactory<out MainVcsLogUi>,
                                                            private val logTabLocation: VcsLogTabLocation) : VcsLogUiFactory<MainVcsLogUi> {
    override fun createLogUi(project: Project, logData: VcsLogData): MainVcsLogUi {
      val ui = factory.createLogUi(project, logData)
      uiProperties.addTab(ui.id, logTabLocation)
      Disposer.register(ui) {
        if (this@VcsLogTabsManager.project.isDisposed || isLogDisposing) return@register // need to restore the tab after project/log is recreated
        uiProperties.removeTab(ui.id) // tab is closed by a user
      }
      return ui
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogTabsManager::class.java)
    private val TAB_GROUP_ID = TabGroupId(VcsLogContentProvider.TAB_NAME, { VcsLogBundle.message("vcs.log.tab.name") }, true)

    private fun generateShortDisplayName(ui: VcsLogUi): @TabTitle String {
      val filters = ui.filterUi.filters
      return if (filters.isEmpty) "" else StringUtil.shortenTextWithEllipsis(filters.getPresentation(), 150, 20)
    }

    fun getFullName(shortName: @TabTitle String): @TabTitle String {
      return ContentUtilEx.getFullName(VcsLogBundle.message("vcs.log.tab.name"), shortName)
    }

    @JvmStatic
    fun generateDisplayName(ui: VcsLogUi): @TabTitle String {
      return getFullName(generateShortDisplayName(ui))
    }

    private fun generateTabId(manager: VcsLogManager): @NonNls String {
      val existingIds = manager.logUis.map { it.id }.toSet()
      var newId: String
      do {
        newId = UUID.randomUUID().toString()
      }
      while (existingIds.contains(newId))
      return newId
    }
  }
}