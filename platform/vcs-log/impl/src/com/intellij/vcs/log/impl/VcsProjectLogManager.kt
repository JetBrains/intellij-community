// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.UiImmediate
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.IJSwingUtilities
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.showCommit
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.showCommitSync
import com.intellij.vcs.log.impl.VcsLogTabsManager.Companion.onDisplayNameChange
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import java.util.function.BiConsumer

internal class VcsProjectLogManager(
  project: Project,
  private val parentCs: CoroutineScope,
  uiProperties: VcsLogProjectTabsProperties,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  recreateHandler: BiConsumer<in VcsLogErrorHandler.Source, in Throwable>,
) : VcsLogManager(project, uiProperties, logProviders, getProjectLogName(logProviders), false,
                  VcsLogSharedSettings.isIndexSwitchedOn(project), recreateHandler) {
  private val tabsManager = VcsLogTabsManager(project, uiProperties, this)
  override val tabs: Set<String> = uiProperties.tabs.keys

  private lateinit var mainUiCs: CoroutineScope

  private val mainUiState = MutableStateFlow<MainVcsLogUi?>(null)
  val mainUi: MainVcsLogUi? get() = mainUiState.value

  @RequiresEdt
  fun createUi() {
    mainUiCs = parentCs.childScope("UI scope of $name", Dispatchers.UiImmediate)

    // need EDT because of immediate toolbar update
    mainUiCs.launch(Dispatchers.EdtImmediate) {
      project.serviceAsync<VcsLogContentProvider.ContentHolder>().contentState.collect { content ->
        if (content != null) {
          val ui = createLogUi(getMainLogUiFactory(MAIN_LOG_ID, null), VcsLogTabLocation.TOOL_WINDOW, false)
          content.displayName = VcsLogTabsUtil.generateDisplayName(ui)
          ui.onDisplayNameChange {
            content.displayName = VcsLogTabsUtil.generateDisplayName(ui)
          }

          val panel = VcsLogPanel(this@VcsProjectLogManager, ui)
          content.component = panel
          IJSwingUtilities.updateComponentTreeUI(content.component)
          mainUiState.value = ui
        }
        else {
          mainUiState.getAndUpdate { null }?.let {
            Disposer.dispose(it)
          }
        }
      }
    }

    tabsManager.createTabs()
  }

  override suspend fun awaitMainUi(): MainVcsLogUi = mainUiCs.async {
    mainUiState.filterNotNull().first()
  }.await()

  override fun runInMainUi(consumer: (MainVcsLogUi) -> Unit) {
    mainUiCs.launch {
      val mainUi = mainUiState.filterNotNull().first()
      consumer(mainUi)
    }
  }

  @RequiresEdt
  fun openNewLogTab(location: VcsLogTabLocation, filters: VcsLogFilterCollection): MainVcsLogUi {
    return tabsManager.openAnotherLogTab(filters, location)
  }

  override suspend fun showCommit(hash: Hash, root: VirtualFile, requestFocus: Boolean): Boolean =
    withContext(Dispatchers.EDT) {
      if (isDisposed) return@withContext false
      showCommitInLogTab(hash, root, requestFocus) { true } != null
    }

  override suspend fun showCommit(hash: Hash, root: VirtualFile, filePath: FilePath, requestFocus: Boolean): Boolean =
    withContext(Dispatchers.EDT) {
      if (isDisposed) return@withContext false
      val logUi = showCommitInLogTab(hash, root, false) { logUi ->
        // Structure filter might prevent us from navigating to FilePath
        val hasFilteredChanges = logUi.properties.exists(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES) &&
                                 logUi.properties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES] &&
                                 !logUi.properties.getFilterValues(VcsLogFilterCollection.STRUCTURE_FILTER.name).isNullOrEmpty()
        return@showCommitInLogTab !hasFilteredChanges
      } ?: return@withContext false

      logUi.selectFilePath(filePath, requestFocus)
      true
    }

  /**
   * Show given commit in the changes view tool window in the log tab matching a given predicate:
   * - Try using one of the currently selected tabs if possible.
   * - Otherwise try main log tab.
   * - Otherwise create a new tab without filters and show commit there.
   */
  private suspend fun showCommitInLogTab(
    hash: Hash,
    root: VirtualFile,
    requestFocus: Boolean,
    predicate: (MainVcsLogUi) -> Boolean,
  ): MainVcsLogUi? {
    if (!containsCommit(hash, root)) {
      if (isLogUpToDate) return null
      waitForRefresh()
      if (!containsCommit(hash, root)) return null
    }

    // At this point we know that commit exists in permanent graph.
    // Try finding it in the opened tabs or open a new tab if none has a matching filter.
    // We will skip tabs that are not refreshed yet, as it may be slow.

    val window = VcsLogContentUtil.getToolWindow(project) ?: return null
    if (!window.isVisible) {
      suspendCancellableCoroutine { continuation ->
        window.activate { continuation.resumeWith(Result.success(Unit)) }
      }
    }

    val selectedUis = getVisibleLogUis(VcsLogTabLocation.TOOL_WINDOW).filterIsInstance<MainVcsLogUi>()
    selectedUis.find { ui -> predicate(ui) && ui.showCommitSync(hash, root, requestFocus) }?.let { return it }

    val mainLogContent = VcsLogContentUtil.findMainLog(window.contentManager)
    if (mainLogContent != null) {
      ChangesViewContentManager.getInstanceImpl(project)?.initLazyContent(mainLogContent)

      val mainLogUi = awaitMainUi()
      if (!selectedUis.contains(mainLogUi)) {
        mainLogUi.refresher.setValid(true, false) // since the main ui is not visible, it needs to be validated to find the commit
        if (predicate(mainLogUi) && mainLogUi.showCommitSync(hash, root, requestFocus)) {
          window.contentManager.setSelectedContent(mainLogContent)
          return mainLogUi
        }
      }
    }

    val otherUis = getLogUis(VcsLogTabLocation.TOOL_WINDOW).filterIsInstance<MainVcsLogUi>() - selectedUis.toSet()
    otherUis.find { ui ->
      ui.refresher.setValid(true, false)
      predicate(ui) && ui.showCommitSync(hash, root, requestFocus)
    }?.let { ui ->
      VcsLogContentUtil.selectLogUi(project, ui, requestFocus)
      return ui
    }

    val newUi = openNewLogTab(VcsLogTabLocation.TOOL_WINDOW, VcsLogFilterObject.EMPTY_COLLECTION)
    if (newUi.showCommit(hash, root, requestFocus)) return newUi
    return null
  }

  private fun containsCommit(hash: Hash, root: VirtualFile): Boolean {
    if (!dataManager.storage.containsCommit(CommitId(hash, root))) return false

    @Suppress("UNCHECKED_CAST")
    val permanentGraphInfo = dataManager.dataPack.permanentGraph as? PermanentGraphInfo<VcsLogCommitStorageIndex> ?: return true

    val commitIndex = dataManager.storage.getCommitIndex(hash, root)
    val nodeId = permanentGraphInfo.permanentCommitsInfo.getNodeId(commitIndex)
    return nodeId != VcsLogUiEx.COMMIT_NOT_FOUND
  }

  @RequiresEdt
  override fun disposeUi() {
    Disposer.dispose(tabsManager)
    mainUiCs.cancel()
    mainUiState.getAndUpdate { null }?.let {
      Disposer.dispose(it)
    }
    super.disposeUi()
  }

  @RequiresBackgroundThread
  override fun dispose() {
    super.dispose()

    val storageImpl = dataManager.storage as? VcsLogStorageImpl ?: return
    if (!storageImpl.isDisposed) {
      thisLogger().error("Storage for $name was not disposed")
      Disposer.dispose(storageImpl)
    }
  }

  fun toolWindowShown(toolWindow: ToolWindow) {
    tabsManager.toolWindowShown(toolWindow)
  }
}

internal fun getProjectLogName(logProviders: Map<VirtualFile, VcsLogProvider>): String {
  return "Vcs Project Log for " + VcsLogUtil.getProvidersMapText(logProviders)
}