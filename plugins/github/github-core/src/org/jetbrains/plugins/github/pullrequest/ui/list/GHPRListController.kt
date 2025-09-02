// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.list

import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel

interface GHPRListController {
  fun reloadList()
}

internal class GHPRListControllerImpl(private val listVm: GHPRListViewModel)
  : GHPRListController {

  override fun reloadList() {
    listVm.reload()
  }
}