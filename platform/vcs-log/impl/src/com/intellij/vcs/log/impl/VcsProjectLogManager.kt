// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PairConsumer
import com.intellij.vcs.log.VcsLogProvider

class VcsProjectLogManager(project: Project, uiProperties: VcsLogProjectTabsProperties, logProviders: Map<VirtualFile, VcsLogProvider>,
                           recreateHandler: PairConsumer<in VcsLogErrorHandler.Source, in Throwable>) :
  VcsLogManager(project, uiProperties, logProviders, false, recreateHandler) {

  val tabsManager = VcsLogTabsManager(project, uiProperties, this)

  internal fun createUi() {
    tabsManager.createTabs()
  }

  override fun disposeUi() {
    tabsManager.disposeTabs()
    super.disposeUi()
  }
}