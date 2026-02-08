// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.UiImmediate
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.ui.ToolWindowLazyContent
import com.intellij.util.ContentUtilEx
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.showCommit
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.showCommitSync
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
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
   * Show a commit [hash] at [root] in the changes view tool window in the log tab matching a given [predicate]
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
    val window = VcsLogContentUtil.getToolWindow(project) ?: return null

    return tryShowCommitSync(window, hash, root, requestFocus, predicate)
           ?: showCommitInEmptyTab(window, hash, root, requestFocus, predicate)
  }

  /**
   * Tries to show a commit in the given log tab only if some tab already contains the commit
   *
   * Tries the selected tab first, then the main tab, and then iterates the rest
   */
  private suspend fun tryShowCommitSync(
    toolWindow: ToolWindow,
    hash: Hash,
    root: VirtualFile,
    requestFocus: Boolean,
    predicate: (MainVcsLogUi) -> Boolean,
  ): MainVcsLogUi? {
    // if content was not created yet, we definitely won't find the commit immediately
    val contentManager = toolWindow.contentManagerIfCreated ?: return null

    fun VcsLogUiEx.showIfPossible(): MainVcsLogUi? =
      asSafely<MainVcsLogUi>()
        ?.takeIf(predicate)
        ?.takeIf {
          if (!it.mainComponent.isShowing) {
            // since the ui is not visible, it needs to be validated to find the commit
            it.refresher.setValid(true, false)
          }
          it.showCommitSync(hash, root, false)
        }

    val selectedContent = contentManager.selectedContent
    val mainContent = VcsLogContentUtil.findMainLog(contentManager)

    if (selectedContent != null && selectedContent != mainContent) {
      val ui = VcsLogContentUtil.getLogUi(selectedContent.component)?.showIfPossible()
      if (ui != null) {
        toolWindow.activateOrShow(requestFocus)
        return ui
      }
    }

    if (mainContent != null) {
      ToolWindowLazyContent.initLazyContent(mainContent)
      val ui = mainUiState.filterNotNull().first().showIfPossible()
      if (ui != null) {
        contentManager.setSelectedContent(mainContent)
        toolWindow.activateOrShow(requestFocus)
        return ui
      }
    }

    for ((content, component) in contentManager.contentComponentSequence()) {
      if (content == mainContent || content == selectedContent) continue
      val ui = VcsLogContentUtil.getLogUi(component)?.showIfPossible()
      if (ui != null) {
        ContentUtilEx.selectContent(contentManager, component, false)
        toolWindow.activateOrShow(requestFocus)
        return ui
      }
    }

    return null
  }

  /**
   * Searches a commit in a tab without any filters
   *
   * Picks the first tab that has no filters or opens a new one
   * Tab lookup order is:
   * selected -> main -> existing -> new
   */
  private suspend fun showCommitInEmptyTab(
    toolWindow: ToolWindow,
    hash: Hash,
    root: VirtualFile,
    requestFocus: Boolean,
    predicate: (MainVcsLogUi) -> Boolean,
  ): MainVcsLogUi? {
    // will potentially init the content manager
    val contentManager = toolWindow.contentManager

    suspend fun VcsLogUiEx.showIfPossible(): MainVcsLogUi? =
      asSafely<MainVcsLogUi>()
        ?.takeIf { predicate(it) && it.filterUi.filters.isEmpty }
        ?.takeIf {
          it.showCommit(hash, root, false)
        }

    val selectedContent = contentManager.selectedContent
    val mainContent = VcsLogContentUtil.findMainLog(contentManager)

    if (selectedContent != null && selectedContent != mainContent) {
      val ui = VcsLogContentUtil.getLogUi(selectedContent.component)?.showIfPossible()
      if (ui != null) {
        toolWindow.activateOrShow(requestFocus)
        return ui
      }
    }

    if (mainContent != null) {
      ToolWindowLazyContent.initLazyContent(mainContent)
      val ui = mainUiState.filterNotNull().first().showIfPossible()
      if (ui != null) {
        contentManager.setSelectedContent(mainContent)
        toolWindow.activateOrShow(requestFocus)
        return ui
      }
    }

    contentManager.contentComponentSequence()
      .firstNotNullOfOrNull { (content, component) ->
        VcsLogContentUtil.getLogUi(component).asSafely<MainVcsLogUi>()
          ?.takeIf { predicate(it) && it.filterUi.filters.isEmpty }
          ?.let { component to it }
      }?.let { (component, ui) ->
        if (ui.showCommit(hash, root, false)) {
          ContentUtilEx.selectContent(contentManager, component, false)
          toolWindow.activateOrShow(requestFocus)
          return ui
        }
      }

    val ui = openNewLogTab(VcsLogTabLocation.TOOL_WINDOW, VcsLogFilterObject.EMPTY_COLLECTION)
    if (!ui.showCommit(hash, root, false)) return null

    toolWindow.activateOrShow(requestFocus)
    return ui
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

private fun ToolWindow.activateOrShow(requestFocus: Boolean) {
  if (requestFocus) {
    activate(null, true, true)
  }
  else {
    show()
  }
}
