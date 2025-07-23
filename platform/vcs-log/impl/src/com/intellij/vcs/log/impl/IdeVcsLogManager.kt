// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.UiImmediate
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.showCommit
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.showCommitSync
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal

internal class IdeVcsLogManager(
  project: Project,
  parentCs: CoroutineScope,
  private val mainUiHolderState: StateFlow<IdeVcsProjectLog.MainUiHolder?>,
  uiProperties: VcsLogProjectTabsProperties,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  errorHandler: ((VcsLogErrorHandler.Source, Throwable) -> Unit)?,
) : VcsLogManager(project, parentCs, uiProperties, logProviders, getProjectLogName(logProviders),
                  VcsLogSharedSettings.isIndexSwitchedOn(project), errorHandler) {
  private val tabsManager = VcsLogTabsManager(project, uiProperties, this)

  private lateinit var mainUiCs: CoroutineScope

  private val mainUiState = MutableStateFlow<MainVcsLogUi?>(null)
  val mainUi: MainVcsLogUi? get() = mainUiState.value

  @RequiresEdt
  fun createUi() {
    mainUiCs = cs.childScope("UI scope of $name", Dispatchers.UiImmediate)

    // need EDT because of immediate toolbar update
    mainUiCs.launch(Dispatchers.EdtImmediate) {
      mainUiHolderState.collect { holder ->
        if (holder != null) {
          val ui = createLogUi(getMainLogUiFactory(MAIN_LOG_ID, null))
          holder.installMainUi(this@IdeVcsLogManager, ui)
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

  @RequiresEdt
  fun runInMainUi(consumer: (MainVcsLogUi) -> Unit) {
    val toolWindow = VcsLogContentUtil.getToolWindow(project)
    if (toolWindow == null) {
      VcsLogContentUtil.showLogIsNotAvailableMessage(project)
      return
    }

    val doRun = Runnable {
      VcsLogContentUtil.selectMainLog(toolWindow)
      // EDT because we want to execute immediately on button press, and it is handled via EDT
      mainUiCs.launch(Dispatchers.EdtImmediate) {
        val mainUi = mainUiState.filterNotNull().first()
        consumer(mainUi)
      }
    }

    if (!toolWindow.isVisible) {
      toolWindow.activate(doRun)
    }
    else {
      doRun.run()
    }
  }

  @RequiresEdt
  fun openNewLogTab(location: VcsLogTabLocation, filters: VcsLogFilterCollection): MainVcsLogUi {
    return tabsManager.openAnotherLogTab(filters, location)
  }

  @RequiresEdt
  fun openNewLogTab(filters: VcsLogFilterCollection): MainVcsLogUi {
    return tabsManager.openAnotherLogTab(filters, VcsLogTabLocation.TOOL_WINDOW)
  }

  override fun getLogUis(): List<VcsLogUi> {
    val tabsIds = tabsManager.getTabs()
    return super.getLogUis().filter { it.id in tabsIds }
  }

  // for statistics
  @Internal
  fun getLogUis(location: VcsLogTabLocation): List<MainVcsLogUi> {
    val tabsIds = tabsManager.getTabs(location)
    return getLogUis().asSequence().filter { it.id in tabsIds }.filterIsInstance<MainVcsLogUi>().toList()
  }

  /**
   * Find a persistent log UI of the given class managed by this manager in a default location
   */
  fun <U : VcsLogUi> findLogUi(clazz: Class<U>, select: Boolean, condition: (U) -> Boolean): U? {
    val toolWindow = VcsLogContentUtil.getToolWindow(project) ?: return null
    val tabsIds = tabsManager.getTabs(VcsLogTabLocation.TOOL_WINDOW)

    val ui = VcsLogContentUtil.findLogUi(toolWindow, clazz, select) {
      it.id in tabsIds && condition(it)
    }
    if (ui != null && select && !toolWindow.isVisible) {
      toolWindow.activate(null)
    }
    return ui
  }

  /**
   * Show given commit in the changes view tool window in the log tab matching a given predicate:
   * - Try using one of the currently selected tabs if possible.
   * - Otherwise try main log tab.
   * - Otherwise create a new tab without filters and show commit there.
   */
  suspend fun showCommitInLogTab(
    hash: Hash,
    root: VirtualFile,
    requestFocus: Boolean,
    predicate: (MainVcsLogUi) -> Boolean,
  ): MainVcsLogUi? {
    if (!awaitContainsCommit(hash, root)) {
      return null
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

    val selectedUi = VcsLogContentUtil.findSelectedLogUi(window) as? MainVcsLogUi
    if (selectedUi != null && predicate(selectedUi)) {
      if (selectedUi.showCommitSync(hash, root, requestFocus)) {
        return selectedUi
      }
    }

    if (selectedUi?.id != MAIN_LOG_ID) {
      val mainLogContent = VcsLogContentUtil.findMainLog(window.contentManager)
      if (mainLogContent != null) {
        ChangesViewContentManager.initLazyContent(mainLogContent)

        val mainLogUi = mainUiState.filterNotNull().first()
        mainLogUi.refresher.setValid(true, false) // since the main ui is not visible, it needs to be validated to find the commit
        if (predicate(mainLogUi) && mainLogUi.showCommitSync(hash, root, requestFocus)) {
          window.contentManager.setSelectedContent(mainLogContent)
          return mainLogUi
        }
      }
    }

    val existingUi = VcsLogContentUtil.findLogUi(window, MainVcsLogUi::class.java, true) {
      if (it === selectedUi && it === mainUi) return@findLogUi false
      it.refresher.setValid(true, false)
      predicate(it) && it.showCommitSync(hash, root, requestFocus)
    }
    if (existingUi != null) return existingUi

    val newUi = openNewLogTab(VcsLogTabLocation.TOOL_WINDOW, VcsLogFilterObject.EMPTY_COLLECTION)
    if (newUi.showCommit(hash, root, requestFocus)) return newUi
    return null
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

  fun toolWindowShown(toolWindow: ToolWindow) {
    tabsManager.toolWindowShown(toolWindow)
  }
}

@Internal
fun getProjectLogName(logProviders: Map<VirtualFile, VcsLogProvider>): String {
  return "Vcs Project Log for " + VcsLogUtil.getProvidersMapText(logProviders)
}