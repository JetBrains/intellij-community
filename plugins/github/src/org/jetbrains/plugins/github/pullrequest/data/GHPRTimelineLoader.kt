// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.ui.CollectionListModel
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader

class GHPRTimelineLoader(progressManager: ProgressManager,
                         requestExecutor: GithubApiRequestExecutor,
                         serverPath: GithubServerPath,
                         repoPath: GithubFullPath,
                         number: Long,
                         listModel: CollectionListModel<GHPRTimelineItem>)
  : GHListLoaderBase<GHPRTimelineItem>(progressManager, listModel) {

  private val loader = SimpleGHGQLPagesLoader(requestExecutor, { p ->
    GHGQLRequests.PullRequest.Timeline.items(serverPath, repoPath.user, repoPath.repository, number, p)
  })

  override fun handleResult(list: List<GHPRTimelineItem>) {
    super.handleResult(list.filter { it !is GHPRTimelineItem.Unknown })
  }

  override fun canLoadMore() = !loading && (loader.hasNext || error != null)

  override fun doLoadMore(indicator: ProgressIndicator) = loader.loadNext(indicator)

  override fun reset() {
    loader.reset()
    super.reset()
    loadMore()
  }
}
