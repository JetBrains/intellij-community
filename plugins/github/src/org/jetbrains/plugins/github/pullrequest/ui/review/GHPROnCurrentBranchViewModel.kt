// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel

interface GHPROnCurrentBranchViewModel {
  val id: GHPRIdentifier

  fun showPullRequest()
}

class GHPROnCurrentBranchViewModelImpl(
  private val projectVm: GHPRToolWindowProjectViewModel,
  override val id: GHPRIdentifier
) : GHPROnCurrentBranchViewModel {

  override fun showPullRequest() {
    projectVm.viewPullRequest(id)
  }
}
