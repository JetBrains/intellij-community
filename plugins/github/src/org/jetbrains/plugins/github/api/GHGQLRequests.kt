// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
import org.jetbrains.plugins.github.api.data.GHConnection
import org.jetbrains.plugins.github.api.data.GHRepositoryPermission
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPageInfo
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPagedRequestResponse
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRequestPagination
import org.jetbrains.plugins.github.api.data.graphql.query.GHGQLSearchQueryResponse
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem

object GHGQLRequests {
  object Repo {
    fun findPermission(repository: GHRepositoryCoordinates): GQLQuery<GHRepositoryPermission?> {
      return GQLQuery.OptionalTraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.findRepositoryPermission,
                                              mapOf("repoOwner" to repository.repositoryPath.owner,
                                                    "repoName" to repository.repositoryPath.repository),
                                              GHRepositoryPermission::class.java,
                                              "repository")
    }
  }

  object PullRequest {
    fun findOne(repository: GHRepositoryCoordinates, number: Long): GQLQuery<GHPullRequest?> {
      return GQLQuery.OptionalTraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.findPullRequest,
                                              mapOf("repoOwner" to repository.repositoryPath.owner,
                                                    "repoName" to repository.repositoryPath.repository,
                                                    "number" to number),
                                              GHPullRequest::class.java,
                                              "repository", "pullRequest")
    }

    fun search(server: GithubServerPath, query: String, pagination: GHGQLRequestPagination? = null)
      : GQLQuery<GHGQLSearchQueryResponse<GHPullRequestShort>> {

      return GQLQuery.Parsed(server.toGraphQLUrl(), GHGQLQueries.issueSearch,
                             mapOf("query" to query,
                                   "pageSize" to pagination?.pageSize,
                                   "cursor" to pagination?.afterCursor),
                             PRSearch::class.java)
    }

    private class PRSearch(search: SearchConnection<GHPullRequestShort>)
      : GHGQLSearchQueryResponse<GHPullRequestShort>(search)

    fun reviewThreads(repository: GHRepositoryCoordinates, number: Long,
                      pagination: GHGQLRequestPagination? = null): GQLQuery<GHGQLPagedRequestResponse<GHPullRequestReviewThread>> {
      return GQLQuery.TraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestReviewThreads,
                                      mapOf("repoOwner" to repository.repositoryPath.owner,
                                            "repoName" to repository.repositoryPath.repository,
                                            "number" to number,
                                            "pageSize" to pagination?.pageSize,
                                            "cursor" to pagination?.afterCursor),
                                      ThreadsConnection::class.java,
                                      "repository", "pullRequest", "reviewThreads")
    }

    private class ThreadsConnection(pageInfo: GHGQLPageInfo, nodes: List<GHPullRequestReviewThread>)
      : GHConnection<GHPullRequestReviewThread>(pageInfo, nodes)

    object Timeline {
      fun items(server: GithubServerPath, repoOwner: String, repoName: String, number: Long,
                pagination: GHGQLRequestPagination? = null)
        : GQLQuery<GHGQLPagedRequestResponse<GHPRTimelineItem>> {

        return GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.pullRequestTimeline,
                                        mapOf("repoOwner" to repoOwner,
                                              "repoName" to repoName,
                                              "number" to number,
                                              "pageSize" to pagination?.pageSize,
                                              "cursor" to pagination?.afterCursor,
                                              "since" to pagination?.since),
                                        TimelineConnection::class.java,
                                        "repository", "pullRequest", "timelineItems")
      }

      private class TimelineConnection(pageInfo: GHGQLPageInfo, nodes: List<GHPRTimelineItem>)
        : GHConnection<GHPRTimelineItem>(pageInfo, nodes)
    }
  }
}