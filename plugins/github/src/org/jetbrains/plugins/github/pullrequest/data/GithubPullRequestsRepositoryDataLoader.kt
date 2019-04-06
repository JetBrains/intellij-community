// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubUser

internal interface GithubPullRequestsRepositoryDataLoader {
  val collaboratorsWithPushAccess: List<GithubUser>
  val issuesAssignees: List<GithubUser>
  val issuesLabels: List<GithubIssueLabel>
  fun reset()
}