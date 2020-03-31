// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
import org.jetbrains.plugins.github.api.data.GHConnection
import org.jetbrains.plugins.github.api.data.GHNodes
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHRepositoryPermission
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPageInfo
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPagedRequestResponse
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRequestPagination
import org.jetbrains.plugins.github.api.data.graphql.query.GHGQLSearchQueryResponse
import org.jetbrains.plugins.github.api.data.pullrequest.*
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem

object GHGQLRequests {
  object Organization {

    object Team {
      fun findAll(server: GithubServerPath, organization: String,
                  pagination: GHGQLRequestPagination? = null): GQLQuery<GHGQLPagedRequestResponse<GHTeam>> {

        return GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.findOrganizationTeams,
                                        mapOf("organization" to organization,
                                              "pageSize" to pagination?.pageSize,
                                              "cursor" to pagination?.afterCursor),
                                        TeamsConnection::class.java,
                                        "organization", "teams")
      }

      fun findByUserLogins(server: GithubServerPath, organization: String, logins: List<String>,
                           pagination: GHGQLRequestPagination? = null): GQLQuery<GHGQLPagedRequestResponse<GHTeam>> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.findOrganizationTeams,
                                 mapOf("organization" to organization,
                                       "logins" to logins,
                                       "pageSize" to pagination?.pageSize,
                                       "cursor" to pagination?.afterCursor),
                                 TeamsConnection::class.java,
                                 "organization", "teams")

      private class TeamsConnection(pageInfo: GHGQLPageInfo, nodes: List<GHTeam>)
        : GHConnection<GHTeam>(pageInfo, nodes)
    }
  }

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

    fun mergeabilityData(repository: GHRepositoryCoordinates, number: Long): GQLQuery<GHPullRequestMergeabilityData?> =
      GQLQuery.OptionalTraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestMergeabilityData,
                                       mapOf("repoOwner" to repository.repositoryPath.owner,
                                             "repoName" to repository.repositoryPath.repository,
                                             "number" to number),
                                       GHPullRequestMergeabilityData::class.java,
                                       "repository", "pullRequest").apply {
        acceptMimeType = "application/vnd.github.antiope-preview+json,application/vnd.github.merge-info-preview+json"
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

    fun commits(repository: GHRepositoryCoordinates, number: Long,
                pagination: GHGQLRequestPagination? = null): GQLQuery<GHGQLPagedRequestResponse<GHPullRequestCommitShort>> {
      return GQLQuery.TraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestCommits,
                                      mapOf("repoOwner" to repository.repositoryPath.owner,
                                            "repoName" to repository.repositoryPath.repository,
                                            "number" to number,
                                            "pageSize" to pagination?.pageSize,
                                            "cursor" to pagination?.afterCursor),
                                      CommitsConnection::class.java,
                                      "repository", "pullRequest", "commits")
    }

    private class CommitsConnection(pageInfo: GHGQLPageInfo, nodes: List<GHPullRequestCommitShort>)
      : GHConnection<GHPullRequestCommitShort>(pageInfo, nodes)

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

    object Review {

      fun create(server: GithubServerPath, pullRequestId: String, event: GHPullRequestReviewEvent, body: String?): GQLQuery<Any> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.createReview,
                                 mapOf("pullRequestId" to pullRequestId,
                                       "event" to event,
                                       "body" to body),
                                 Any::class.java)

      fun submit(server: GithubServerPath, reviewId: String, event: GHPullRequestReviewEvent, body: String?): GQLQuery<Any> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.submitReview,
                                 mapOf("reviewId" to reviewId,
                                       "event" to event,
                                       "body" to body),
                                 Any::class.java)

      fun delete(server: GithubServerPath, reviewId: String): GQLQuery<Any> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.deleteReview,
                                 mapOf("reviewId" to reviewId),
                                 Any::class.java)

      fun pendingReviews(server: GithubServerPath, pullRequestId: String): GQLQuery<GHNodes<GHPullRequestPendingReview>> {
        return GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.pendingReview,
                                        mapOf("pullRequestId" to pullRequestId),
                                        PendingReviewNodes::class.java,
                                        "node", "reviews")
      }

      private class PendingReviewNodes(nodes: List<GHPullRequestPendingReview>) :
        GHNodes<GHPullRequestPendingReview>(nodes)

      fun addComment(server: GithubServerPath,
                     pullRequestId: String?, reviewId: String?,
                     body: String, commitSha: String, fileName: String, diffLine: Int)
        : GQLQuery<GHPullRequestReviewCommentWithPendingReview> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.addReviewComment,
                                 mapOf("pullRequestId" to pullRequestId,
                                       "reviewId" to reviewId,
                                       "body" to body,
                                       "commit" to commitSha,
                                       "file" to fileName,
                                       "position" to diffLine),
                                 GHPullRequestReviewCommentWithPendingReview::class.java,
                                 "addPullRequestReviewComment", "comment")

      fun getCommentBody(server: GithubServerPath, commentId: String): GQLQuery<String> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.getReviewCommentBody,
                                 mapOf("id" to commentId),
                                 String::class.java,
                                 "node", "body")

      fun deleteComment(server: GithubServerPath, commentId: String): GQLQuery<GHPullRequestPendingReview> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.deleteReviewComment,
                                 mapOf("id" to commentId),
                                 GHPullRequestPendingReview::class.java,
                                 "deletePullRequestReviewComment", "pullRequestReview")

      fun updateComment(server: GithubServerPath, commentId: String, newText: String): GQLQuery<GHPullRequestReviewComment> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.updateReviewComment,
                                 mapOf("id" to commentId,
                                       "body" to newText),
                                 GHPullRequestReviewComment::class.java,
                                 "updatePullRequestReviewComment", "pullRequestReviewComment")

      fun resolveThread(server: GithubServerPath, threadId: String): GQLQuery<GHPullRequestReviewThread> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.resolveReviewThread,
                                 mapOf("threadId" to threadId),
                                 GHPullRequestReviewThread::class.java,
                                 "resolveReviewThread", "thread")

      fun unresolveThread(server: GithubServerPath, threadId: String): GQLQuery<GHPullRequestReviewThread> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.unresolveReviewThread,
                                 mapOf("threadId" to threadId),
                                 GHPullRequestReviewThread::class.java,
                                 "unresolveReviewThread", "thread")
    }
  }
}