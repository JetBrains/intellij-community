// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.issue

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubIssue
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import java.io.IOException

object GithubIssuesLoadingHelper {
  @JvmOverloads
  @JvmStatic
  @Throws(IOException::class)
  fun load(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, server: GithubServerPath,
           owner: String, repo: String, withClosed: Boolean, maximum: Int = 100, assignee: String? = null): List<GithubIssue> {
    return GithubApiPagesLoader.load(executor, indicator,
                                     GithubApiRequests.Repos.Issues.pages(server, owner, repo,
                                                                           if (withClosed) "all" else "open", assignee), maximum)
  }

  @JvmOverloads
  @JvmStatic
  @Throws(IOException::class)
  fun search(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, server: GithubServerPath,
             owner: String, repo: String, withClosed: Boolean, assignee: String? = null, query: String? = null)
    : List<GithubIssue> {

    return GithubApiPagesLoader.loadAll(executor, indicator,
                                        GithubApiRequests.Search.Issues.pages(server,
                                                                               GithubFullPath(owner, repo),
                                                                               if (withClosed) null else "open", assignee, query))
  }
}