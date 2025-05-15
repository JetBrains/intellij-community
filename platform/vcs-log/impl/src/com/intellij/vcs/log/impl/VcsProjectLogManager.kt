// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.util.VcsLogUtil
import kotlinx.coroutines.guava.await
import java.util.function.BiConsumer

internal class VcsProjectLogManager(
  project: Project,
  uiProperties: VcsLogProjectTabsProperties,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  recreateHandler: BiConsumer<in VcsLogErrorHandler.Source, in Throwable>,
) : VcsLogManager(project, uiProperties, logProviders, getProjectLogName(logProviders), false,
                  VcsLogSharedSettings.isIndexSwitchedOn(project), recreateHandler) {
  private val tabsManager = VcsLogTabsManager(project, uiProperties, this)
  override val tabs: Set<String> = uiProperties.tabs.keys

  val mainUi: MainVcsLogUi?
    get() = getVcsLogContentProvider(project)?.ui

  @RequiresEdt
  fun createUi() {
    getVcsLogContentProvider(project)?.addMainUi(this)
    tabsManager.createTabs()
  }

  override suspend fun awaitMainUi(): MainVcsLogUi? {
    return getVcsLogContentProvider(project)?.waitMainUiCreation()?.await()
  }

  override fun runInMainUi(consumer: (MainVcsLogUi) -> Unit) {
    getVcsLogContentProvider(project)!!.executeOnMainUiCreated(consumer)
  }

  @RequiresEdt
  fun openNewLogTab(location: VcsLogTabLocation, filters: VcsLogFilterCollection): MainVcsLogUi {
    return tabsManager.openAnotherLogTab(filters, location)
  }

  @RequiresEdt
  override fun disposeUi() {
    Disposer.dispose(tabsManager)
    getVcsLogContentProvider(project)?.disposeMainUi()
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

private fun getVcsLogContentProvider(project: Project): VcsLogContentProvider? {
  for (ep in ChangesViewContentEP.EP_NAME.getExtensions(project)) {
    if (ep.getClassName() == VcsLogContentProvider::class.java.name) {
      return ep.cachedInstance as VcsLogContentProvider?
    }
  }
  return null
}
