// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.list

import org.jetbrains.plugins.github.pullrequest.data.GHPRListLoader
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService

interface GHPRListController {
  fun refreshList()
}

internal class GHPRListControllerImpl(
  private val repositoryDataService: GHPRRepositoryDataService,
  private val listLoader: GHPRListLoader)
  : GHPRListController {

  override fun refreshList() {
    listLoader.reset()
    repositoryDataService.resetData()
  }
}