// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO
import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.ResultUtil.processErrorAndGet
import com.intellij.collaboration.util.SequenceItem
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

private val LOG = logger<GHPRTimelineServiceImpl>()

internal class GHPRTimelineServiceImpl(
  private val requestExecutor: GithubApiRequestExecutor,
  private val repository: GHRepositoryCoordinates,
) : GHPRTimelineService {
  override fun getTimelineItems(id: GHPRIdentifier): ComputableSequence<ListPart<GHPRTimelineItem>> =
    ComputableSequence.byCursor<String, ListPart<GHPRTimelineItem>>(null) { lastCursor ->
      val pagination = GraphQLRequestPagination(afterCursor = lastCursor)
      val page = loadPage(id, pagination)
      if (page.nodes.isEmpty()) {
        return@byCursor null
      }

      val lastCursor = page.pageInfo.endCursor ?: run {
        LOG.warn("Non-empty list of items ${page.nodes}, but the last cursor is null. Won't load any more items.")
        return@byCursor null
      }
      val hasMorePages = page.pageInfo.hasNextPage

      SequenceItem(page.nodes, isLast = !hasMorePages) to lastCursor
    }

  private suspend fun loadPage(id: GHPRIdentifier, pagination: GraphQLRequestPagination): GraphQLPagedResponseDataDTO<GHPRTimelineItem> {
    val request = GHGQLRequests.PullRequest.Timeline.items(repository.serverPath,
                                                           repository.repositoryPath.owner,
                                                           repository.repositoryPath.repository,
                                                           id.number, pagination)
    return runCatching {
      requestExecutor.executeSuspend(request)
    }.processErrorAndGet { e ->
      LOG.info("Error occurred while loading PR $id timeline items page $pagination", e)
    }
  }
}