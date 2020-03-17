// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineMergingModel

class GHPRTimelineLoader(progressManager: ProgressManager,
                         requestExecutor: GithubApiRequestExecutor,
                         serverPath: GithubServerPath,
                         repoPath: GHRepositoryPath,
                         number: Long,
                         val listModel: GHPRTimelineMergingModel)
  : GHGQLPagedListLoader<GHPRTimelineItem>(progressManager,
                                           SimpleGHGQLPagesLoader(requestExecutor, { p ->
                                             GHGQLRequests.PullRequest.Timeline.items(serverPath, repoPath.owner, repoPath.repository,
                                                                                      number, p)
                                           }, true)) {
  override val hasLoadedItems: Boolean
    get() = listModel.size != 0

  override fun handleResult(list: List<GHPRTimelineItem>) {
    listModel.add(list.filter { it !is GHPRTimelineItem.Unknown })
  }

  override fun reset() {
    super.reset()
    listModel.removeAll()
    loadMore()
  }
}
