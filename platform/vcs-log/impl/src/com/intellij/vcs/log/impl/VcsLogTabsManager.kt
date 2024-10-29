// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.TabGroupId
import com.intellij.util.ContentUtilEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogContentUtil.getToolWindow
import com.intellij.vcs.log.impl.VcsLogContentUtil.openLogTab
import com.intellij.vcs.log.impl.VcsLogContentUtil.updateLogUiName
import com.intellij.vcs.log.impl.VcsLogManager.VcsLogUiFactory
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.GraphOptionsUtil.presentationForTabTitle
import com.intellij.vcs.log.visible.filters.getPresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.concurrent.CompletableFuture

class VcsLogTabsManager internal constructor(private val project: Project,
                                             private val uiProperties: VcsLogProjectTabsProperties,
                                             private val logManager: VcsLogManager) {
  private var isLogDisposing = false

  private val futureToolWindow: CompletableFuture<ToolWindow> = CompletableFuture()

  // for statistics
  @get:ApiStatus.Internal
  val tabs: Set<String> get() = uiProperties.tabs.keys

  internal fun createTabs() {
    val savedTabs = uiProperties.tabs
    if (savedTabs.isEmpty()) return

    val editorTabs = savedTabs.filterValues { it === VcsLogTabLocation.EDITOR }.keys
    val toolWindowTabs = savedTabs.filterValues { it === VcsLogTabLocation.TOOL_WINDOW }.keys

    if (savedTabs.any { it.value === VcsLogTabLocation.STANDALONE }) {
      LOG.warn("Reopening standalone tabs is not supported")
    }

    if (toolWindowTabs.isNotEmpty() || editorTabs.isNotEmpty()) {
      futureToolWindow.thenAccept { toolWindow ->
        if (!LOG.assertTrue(!logManager.isDisposed, "Attempting to open tabs on disposed VcsLogManager")) return@thenAccept
        LOG.debug("Reopening toolwindow tabs with ids: $toolWindowTabs")
        toolWindowTabs.forEach { openToolWindowLogTab(toolWindow, it, false, null) }
        editorTabs.forEach { openToolWindowLogTab(toolWindow, it, false, null) }
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

  internal fun disposeTabs() {
    isLogDisposing = true
  }

  internal fun toolWindowShown(toolWindow: ToolWindow) {
    futureToolWindow.complete(toolWindow)
  }

  fun openAnotherLogTab(filters: VcsLogFilterCollection, location: VcsLogTabLocation): MainVcsLogUi {
    val tabId = generateTabId(logManager)
    uiProperties.resetState(tabId)
    if (location === VcsLogTabLocation.EDITOR) {
      error("Unsupported")
    }
    else if (location === VcsLogTabLocation.TOOL_WINDOW) {
      val toolWindow = VcsLogContentUtil.getToolWindowOrThrow(project)
      futureToolWindow.complete(toolWindow)
      return openToolWindowLogTab(toolWindow, tabId, true, filters)
    }
    throw UnsupportedOperationException("Only log in editor or tool window is supported")
  }

  private fun openToolWindowLogTab(toolWindow: ToolWindow, tabId: String, focus: Boolean,
                                   filters: VcsLogFilterCollection?): MainVcsLogUi {
    val factory = getPersistentVcsLogUiFactory(tabId, VcsLogTabLocation.TOOL_WINDOW, filters)
    val ui = openLogTab(logManager, factory, toolWindow, TAB_GROUP_ID, { u: MainVcsLogUi -> generateShortDisplayName(u) }, focus)
    ui.onDisplayNameChange { updateLogUiName(project, ui) }
    return ui
  }

  @RequiresEdt
  @ApiStatus.Internal
  fun getPersistentVcsLogUiFactory(tabId: String,
                                   location: VcsLogTabLocation,
                                   filters: VcsLogFilterCollection?): VcsLogUiFactory<MainVcsLogUi> {
    return PersistentVcsLogUiFactory(logManager.getMainLogUiFactory(tabId, filters), location)
  }

  private inner class PersistentVcsLogUiFactory(private val factory: VcsLogUiFactory<out MainVcsLogUi>,
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
    val TAB_GROUP_ID = TabGroupId(VcsLogContentProvider.TAB_NAME, { VcsLogBundle.message("vcs.log.tab.name") }, true)

    private fun generateShortDisplayName(ui: VcsLogUiEx): @TabTitle String {
      val options = ui.properties.
      getOrNull(MainVcsLogUiProperties.GRAPH_OPTIONS)
      val optionsPresentation = options?.presentationForTabTitle ?: ""
      val filters = ui.filterUi.filters
      val filtersPresentation = if (filters.isEmpty) "" else filters.getPresentation(withPrefix = optionsPresentation.isNotEmpty())
      val presentation = listOf(optionsPresentation, filtersPresentation).filter { it.isNotEmpty() }.joinToString(separator = " ")
      return StringUtil.shortenTextWithEllipsis(presentation, 150, 20)
    }

    fun getFullName(shortName: @TabTitle String): @TabTitle String {
      return ContentUtilEx.getFullName(VcsLogBundle.message("vcs.log.tab.name"), shortName)
    }

    @JvmStatic
    fun generateDisplayName(ui: VcsLogUiEx): @TabTitle String {
      return getFullName(generateShortDisplayName(ui))
    }

    fun MainVcsLogUi.onDisplayNameChange(block: () -> Unit) {
      filterUi.addFilterListener { block() }
      properties.onPropertyChange(this) {
        if (it == MainVcsLogUiProperties.GRAPH_OPTIONS) block()
      }
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

internal class VcsLogToolwindowManagerListener(private val project: Project) : ToolWindowManagerListener {
  override fun toolWindowShown(toolWindow: ToolWindow) {
    if (toolWindow.id == ChangesViewContentManager.TOOLWINDOW_ID) {
      val projectLog = VcsProjectLog.getInstance(project)
      projectLog.createLogInBackground(true)
      projectLog.tabManager?.toolWindowShown(toolWindow)
    }
  }
}
