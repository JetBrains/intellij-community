// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

interface GHPRToolWindowTabComponentController {

  val currentView: GHPRToolWindowViewType

  fun createPullRequest(requestFocus: Boolean = true)

  fun resetNewPullRequestView()

  fun viewList(requestFocus: Boolean = true)

  fun refreshList()

  fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean = true): GHPRCommitBrowserComponentController?

  fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean)

  fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean)
}
