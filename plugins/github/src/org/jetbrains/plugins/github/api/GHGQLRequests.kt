// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO
import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRequestPagination
import org.jetbrains.plugins.github.api.data.graphql.query.GHGQLSearchQueryResponse
import org.jetbrains.plugins.github.api.data.pullrequest.*
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.api.util.GHSchemaPreview

object GHGQLRequests {
  object Organization {

    object Team {
      fun findAll(server: GithubServerPath, organization: String,
                  pagination: GHGQLRequestPagination? = null): GQLQuery<GraphQLPagedResponseDataDTO<GHTeam>> {

        return GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.findOrganizationTeams,
                                        mapOf("organization" to organization,
                                              "pageSize" to pagination?.pageSize,
                                              "cursor" to pagination?.afterCursor),
                                        TeamsConnection::class.java,
                                        "organization", "teams")
      }

      fun findByUserLogins(server: GithubServerPath, organization: String, logins: List<String>,
                           pagination: GHGQLRequestPagination? = null): GQLQuery<GraphQLPagedResponseDataDTO<GHTeam>> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.findOrganizationTeams,
                                 mapOf("organization" to organization,
                                       "logins" to logins,
                                       "pageSize" to pagination?.pageSize,
                                       "cursor" to pagination?.afterCursor),
                                 TeamsConnection::class.java,
                                 "organization", "teams")

      private class TeamsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHTeam>)
        : GHConnection<GHTeam>(pageInfo, nodes)
    }
  }

  object Repo {
    fun find(repository: GHRepositoryCoordinates): GQLQuery<GHRepository?> {
      return GQLQuery.OptionalTraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.findRepository,
                                              mapOf("repoOwner" to repository.repositoryPath.owner,
                                                    "repoName" to repository.repositoryPath.repository),
                                              GHRepository::class.java,
                                              "repository")
    }

    fun getProtectionRules(repository: GHRepositoryCoordinates,
                           pagination: GHGQLRequestPagination? = null): GQLQuery<GraphQLPagedResponseDataDTO<GHBranchProtectionRule>> {
      return GQLQuery.TraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.getProtectionRules,
                                      mapOf("repoOwner" to repository.repositoryPath.owner,
                                            "repoName" to repository.repositoryPath.repository,
                                            "pageSize" to pagination?.pageSize,
                                            "cursor" to pagination?.afterCursor),
                                      ProtectedRulesConnection::class.java,
                                      "repository", "branchProtectionRules")
    }

    private class ProtectedRulesConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHBranchProtectionRule>)
      : GHConnection<GHBranchProtectionRule>(pageInfo, nodes)
  }

  object Comment {

    fun updateComment(server: GithubServerPath, commentId: String, newText: String): GQLQuery<GHComment> =
      GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.updateIssueComment,
                               mapOf("id" to commentId,
                                     "body" to newText),
                               GHComment::class.java,
                               "updateIssueComment", "issueComment")

    fun deleteComment(server: GithubServerPath, commentId: String): GQLQuery<Any?> =
      GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.deleteIssueComment,
                               mapOf("id" to commentId),
                               Any::class.java)
  }

  object PullRequest {
    fun create(repository: GHRepositoryCoordinates,
               repositoryId: String,
               baseRefName: String,
               headRefName: String,
               title: String,
               body: String? = null,
               draft: Boolean? = false): GQLQuery<GHPullRequestShort> {
      return GQLQuery.TraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.createPullRequest,
                                      mapOf("repositoryId" to repositoryId,
                                            "baseRefName" to baseRefName,
                                            "headRefName" to headRefName,
                                            "title" to title,
                                            "body" to body,
                                            "draft" to draft),
                                      GHPullRequestShort::class.java,
                                      "createPullRequest", "pullRequest").apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType
      }
    }

    fun findOne(repository: GHRepositoryCoordinates, number: Long): GQLQuery<GHPullRequest?> {
      return GQLQuery.OptionalTraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.findPullRequest,
                                              mapOf("repoOwner" to repository.repositoryPath.owner,
                                                    "repoName" to repository.repositoryPath.repository,
                                                    "number" to number),
                                              GHPullRequest::class.java,
                                              "repository", "pullRequest").apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType
      }
    }

    fun findByBranches(repository: GHRepositoryCoordinates, baseBranch: String, headBranch: String)
      : GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequest>> =
      GQLQuery.TraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.findOpenPullRequestsByBranches,
                               mapOf("repoOwner" to repository.repositoryPath.owner,
                                     "repoName" to repository.repositoryPath.repository,
                                     "baseBranch" to baseBranch,
                                     "headBranch" to headBranch),
                               PullRequestsConnection::class.java,
                               "repository", "pullRequests").apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType
      }

    private class PullRequestsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPullRequest>)
      : GHConnection<GHPullRequest>(pageInfo, nodes)

    fun update(repository: GHRepositoryCoordinates, pullRequestId: String, title: String?, description: String?): GQLQuery<GHPullRequest> {
      val parameters = mutableMapOf<String, Any>("pullRequestId" to pullRequestId)
      if (title != null) parameters["title"] = title
      if (description != null) parameters["body"] = description
      return GQLQuery.TraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.updatePullRequest, parameters,
                                      GHPullRequest::class.java,
                                      "updatePullRequest", "pullRequest").apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType
      }
    }

    fun markReadyForReview(repository: GHRepositoryCoordinates, pullRequestId: String): GQLQuery<Any?> =
      GQLQuery.Parsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.markPullRequestReadyForReview,
                      mutableMapOf<String, Any>("pullRequestId" to pullRequestId),
                      Any::class.java).apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType
      }

    fun mergeabilityData(repository: GHRepositoryCoordinates, number: Long): GQLQuery<GHPullRequestMergeabilityData?> =
      GQLQuery.OptionalTraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestMergeabilityData,
                                       mapOf("repoOwner" to repository.repositoryPath.owner,
                                             "repoName" to repository.repositoryPath.repository,
                                             "number" to number),
                                       GHPullRequestMergeabilityData::class.java,
                                       "repository", "pullRequest").apply {
        acceptMimeType = "${GHSchemaPreview.CHECKS.mimeType},${GHSchemaPreview.PR_MERGE_INFO.mimeType}"
      }

    fun search(server: GithubServerPath, query: String, pagination: GHGQLRequestPagination? = null)
      : GQLQuery<GHGQLSearchQueryResponse<GHPullRequestShort>> {

      return GQLQuery.Parsed(server.toGraphQLUrl(), GHGQLQueries.issueSearch,
                             mapOf("query" to query,
                                   "pageSize" to pagination?.pageSize,
                                   "cursor" to pagination?.afterCursor),
                             PRSearch::class.java).apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType
      }
    }

    private class PRSearch(search: SearchConnection<GHPullRequestShort>)
      : GHGQLSearchQueryResponse<GHPullRequestShort>(search)

    fun reviewThreads(
      repository: GHRepositoryCoordinates,
      number: Long,
      pagination: GHGQLRequestPagination? = null
    ): GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestReviewThread>> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestReviewThreads,
        parameters(repository, number, pagination),
        ThreadsConnection::class.java, "repository", "pullRequest", "reviewThreads"
      )

    private class ThreadsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPullRequestReviewThread>)
      : GHConnection<GHPullRequestReviewThread>(pageInfo, nodes)

    fun commits(
      repository: GHRepositoryCoordinates,
      number: Long,
      pagination: GHGQLRequestPagination? = null
    ): GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestCommit>> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestCommits,
        parameters(repository, number, pagination),
        CommitsConnection::class.java, "repository", "pullRequest", "commits"
      )

    private class CommitsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPullRequestCommit>)
      : GHConnection<GHPullRequestCommit>(pageInfo, nodes)

    fun files(
      repository: GHRepositoryCoordinates,
      number: Long,
      pagination: GHGQLRequestPagination
    ): GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestChangedFile>> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestFiles,
        parameters(repository, number, pagination),
        FilesConnection::class.java, "repository", "pullRequest", "files"
      )

    private class FilesConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPullRequestChangedFile>) :
      GHConnection<GHPullRequestChangedFile>(pageInfo, nodes)

    fun markFileAsViewed(server: GithubServerPath, pullRequestId: String, path: String): GQLQuery<Unit> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.markFileAsViewed,
        mapOf("pullRequestId" to pullRequestId, "path" to path),
        Unit::class.java
      )

    fun unmarkFileAsViewed(server: GithubServerPath, pullRequestId: String, path: String): GQLQuery<Unit> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.unmarkFileAsViewed,
        mapOf("pullRequestId" to pullRequestId, "path" to path),
        Unit::class.java
      )

    object Timeline {
      fun items(server: GithubServerPath, repoOwner: String, repoName: String, number: Long,
                pagination: GHGQLRequestPagination? = null)
        : GQLQuery<GraphQLPagedResponseDataDTO<GHPRTimelineItem>> {

        return GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.pullRequestTimeline,
                                        mapOf("repoOwner" to repoOwner,
                                              "repoName" to repoName,
                                              "number" to number,
                                              "pageSize" to pagination?.pageSize,
                                              "cursor" to pagination?.afterCursor,
                                              "since" to pagination?.since),
                                        TimelineConnection::class.java,
                                        "repository", "pullRequest", "timelineItems").apply {
          acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType
        }
      }

      private class TimelineConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPRTimelineItem>)
        : GHConnection<GHPRTimelineItem>(pageInfo, nodes)
    }

    object Review {

      fun create(server: GithubServerPath, pullRequestId: String,
                 event: GHPullRequestReviewEvent?, body: String?, commitSha: String?,
                 comments: List<GHPullRequestDraftReviewComment>?,
                 threads: List<GHPullRequestDraftReviewThread>?): GQLQuery<GHPullRequestPendingReview> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.createReview,
                                 mapOf("pullRequestId" to pullRequestId,
                                       "event" to event,
                                       "commitOid" to commitSha,
                                       "comments" to comments,
                                       "threads" to threads,
                                       "body" to body),
                                 GHPullRequestPendingReview::class.java,
                                 "addPullRequestReview", "pullRequestReview")

      fun submit(server: GithubServerPath, reviewId: String, event: GHPullRequestReviewEvent, body: String?): GQLQuery<Any> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.submitReview,
                                 mapOf("reviewId" to reviewId,
                                       "event" to event,
                                       "body" to body),
                                 Any::class.java)

      fun updateBody(server: GithubServerPath, reviewId: String, newText: String): GQLQuery<GHPullRequestReview> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.updateReview,
                                 mapOf("reviewId" to reviewId,
                                       "body" to newText),
                                 GHPullRequestReview::class.java,
                                 "updatePullRequestReview", "pullRequestReview")

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
                     reviewId: String,
                     body: String, commitSha: String, fileName: String, diffLine: Int)
        : GQLQuery<GHPullRequestReviewCommentWithPendingReview> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.addReviewComment,
                                 mapOf("reviewId" to reviewId,
                                       "body" to body,
                                       "commit" to commitSha,
                                       "file" to fileName,
                                       "position" to diffLine),
                                 GHPullRequestReviewCommentWithPendingReview::class.java,
                                 "addPullRequestReviewComment", "comment")

      fun addComment(server: GithubServerPath,
                     reviewId: String,
                     inReplyTo: String,
                     body: String)
        : GQLQuery<GHPullRequestReviewCommentWithPendingReview> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.addReviewComment,
                                 mapOf("reviewId" to reviewId,
                                       "inReplyTo" to inReplyTo,
                                       "body" to body),
                                 GHPullRequestReviewCommentWithPendingReview::class.java,
                                 "addPullRequestReviewComment", "comment")

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

      fun addThread(server: GithubServerPath, reviewId: String,
                    body: String, line: Int, side: Side, startLine: Int, fileName: String): GQLQuery<GHPullRequestReviewThread> =
        GQLQuery.TraversedParsed(server.toGraphQLUrl(), GHGQLQueries.addPullRequestReviewThread,
                                 mapOf("body" to body,
                                       "line" to line,
                                       "path" to fileName,
                                       "pullRequestReviewId" to reviewId,
                                       "side" to side.name,
                                       "startSide" to side.name,
                                       "startLine" to startLine),
                                 GHPullRequestReviewThread::class.java,
                                 "addPullRequestReviewThread", "thread")

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

private fun parameters(
  repository: GHRepositoryCoordinates,
  pullRequestNumber: Long,
  pagination: GHGQLRequestPagination?
): Map<String, Any?> =
  mapOf(
    "repoOwner" to repository.repositoryPath.owner,
    "repoName" to repository.repositoryPath.repository,
    "number" to pullRequestNumber,
    "pageSize" to pagination?.pageSize,
    "cursor" to pagination?.afterCursor
  )