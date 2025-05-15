// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.UiImmediate
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.IJSwingUtilities
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.impl.VcsLogTabsManager.Companion.onDisplayNameChange
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.util.VcsLogUtil
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