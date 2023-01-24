// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext

interface GHPRListController {
  fun refreshList()
}

internal class GHPRListControllerImpl(private val dataContext: GHPRDataContext) : GHPRListController {
  override fun refreshList() {
    dataContext.listLoader.reset()
    dataContext.repositoryDataService.resetData()
  }
}