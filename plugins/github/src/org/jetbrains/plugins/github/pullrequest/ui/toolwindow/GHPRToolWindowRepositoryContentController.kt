// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.util.Key
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

/**
 * Controls the content tied to a certain repository
 */
interface GHPRToolWindowRepositoryContentController {

  val repository: GHRepositoryCoordinates

  fun createPullRequest(requestFocus: Boolean = true)

  fun resetNewPullRequestView()

  fun viewList(requestFocus: Boolean = true)

  fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean = true): GHPRCommitBrowserComponentController?

  fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean)

  fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean)

  companion object {
    val KEY = Key.create<GHPRToolWindowRepositoryContentController>("Github.PullRequests.ToolWindow.Repository.Content.Controller")
  }
}
