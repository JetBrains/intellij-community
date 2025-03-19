// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.BiConsumer

@ApiStatus.Internal
class VcsProjectLogManager(project: Project, uiProperties: VcsLogProjectTabsProperties, logProviders: Map<VirtualFile, VcsLogProvider>,
                           recreateHandler: BiConsumer<in VcsLogErrorHandler.Source, in Throwable>) :
  VcsLogManager(project, uiProperties, logProviders, getProjectLogName(logProviders), false,
                VcsLogSharedSettings.isIndexSwitchedOn(project), recreateHandler) {

  val tabsManager = VcsLogTabsManager(project, uiProperties, this)

  internal fun createUi() {
    getVcsLogContentProvider(myProject)?.addMainUi(this)
    tabsManager.createTabs()
  }

  override fun disposeUi() {
    tabsManager.disposeTabs()
    getVcsLogContentProvider(myProject)?.disposeMainUi()
    super.disposeUi()
  }

  override fun dispose() {
    super.dispose()

    val storageImpl = dataManager.storage as? VcsLogStorageImpl ?: return
    if (!storageImpl.isDisposed) {
      thisLogger().error("Storage for $name was not disposed")
      Disposer.dispose(storageImpl)
    }
  }
}

internal fun getProjectLogName(logProviders: Map<VirtualFile, VcsLogProvider>): String {
  return "Vcs Project Log for " + VcsLogUtil.getProvidersMapText(logProviders)
}