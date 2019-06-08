// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue

object GithubPullRequestKeys {
  @JvmStatic
  val DATA_CONTEXT = DataKey.create<GithubPullRequestsDataContext>("org.jetbrains.plugins.github.pullrequest.datacontext")

  @JvmStatic
  internal val SELECTED_SEARCHED_ISSUE = DataKey.create<GithubSearchedIssue>("org.jetbrains.plugins.github.pullrequest.list.selected")
}