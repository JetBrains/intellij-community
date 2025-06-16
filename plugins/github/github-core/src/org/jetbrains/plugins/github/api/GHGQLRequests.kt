// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO
import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.commit.GHCommitStatusRollupContextDTO
import org.jetbrains.plugins.github.api.data.commit.GHCommitStatusRollupShortDTO
import org.jetbrains.plugins.github.api.data.graphql.query.GHGQLSearchQueryResponse
import org.jetbrains.plugins.github.api.data.pullrequest.*
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType
import org.jetbrains.plugins.github.api.util.GHSchemaPreview
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName

object GHGQLRequests {
  object User {
    fun find(server: GithubServerPath, login: String): GQLQuery<GHUser?> =
      GQLQuery.OptionalTraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.findUser,
        mapOf("login" to login),
        GHUser::class.java,
        "user"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetUser)
        withOperationName("get user")
      }
  }

  object Organization {

    object Team {
      fun findAll(
        server: GithubServerPath, organization: String,
        pagination: GraphQLRequestPagination? = null,
      ): GQLQuery<GraphQLPagedResponseDataDTO<GHTeam>> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.findOrganizationTeams,
          mapOf("organization" to organization,
                "pageSize" to pagination?.pageSize,
                "cursor" to pagination?.afterCursor),
          TeamsConnection::class.java,
          "organization", "teams"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLGetTeamsForOrganization)
          withOperationName("get teams in organization")
        }

      private class TeamsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHTeam> = listOf())
        : GraphQLConnectionDTO<GHTeam>(pageInfo, nodes)
    }
  }

  object Repo {
    fun find(repository: GHRepositoryCoordinates): GQLQuery<GHRepository?> =
      GQLQuery.OptionalTraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.findRepository,
        mapOf("repoOwner" to repository.repositoryPath.owner,
              "repoName" to repository.repositoryPath.repository),
        GHRepository::class.java,
        "repository"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetRepository)
        withOperationName("get repository")
      }

    fun loadPullRequestTemplates(repository: GHRepositoryCoordinates): GQLQuery<List<GHRepositoryPullRequestTemplate>?> =
      GQLQuery.OptionalTraversedParsedList(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.getPullRequestTemplates,
        mapOf("repoOwner" to repository.repositoryPath.owner,
              "repoName" to repository.repositoryPath.repository),
        GHRepositoryPullRequestTemplate::class.java,
        "repository", "pullRequestTemplates"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetPullRequestTemplates)
        withOperationName("get pull request templates")
      }

    fun getProtectionRules(
      repository: GHRepositoryCoordinates,
      pagination: GraphQLRequestPagination? = null,
    ): GQLQuery<GraphQLPagedResponseDataDTO<GHBranchProtectionRule>> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.getProtectionRules,
        mapOf("repoOwner" to repository.repositoryPath.owner,
              "repoName" to repository.repositoryPath.repository,
              "pageSize" to pagination?.pageSize,
              "cursor" to pagination?.afterCursor),
        ProtectedRulesConnection::class.java,
        "repository", "branchProtectionRules"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetBranchProtectionRules)
        withOperationName("get branch protection rules")
      }

    private class ProtectedRulesConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHBranchProtectionRule> = listOf())
      : GraphQLConnectionDTO<GHBranchProtectionRule>(pageInfo, nodes)

    fun getCommitStatus(
      repo: GHRepositoryCoordinates,
      commitHash: String,
    ): GQLQuery<GHCommitStatusRollupShortDTO?> =
      GQLQuery.OptionalTraversedParsed(
        repo.serverPath.toGraphQLUrl(),
        GHGQLQueries.getRepositoryCommitStatus,
        mapOf("repoOwner" to repo.repositoryPath.owner,
              "repoName" to repo.repositoryPath.repository,
              "oid" to commitHash),
        GHCommitStatusRollupShortDTO::class.java,
        "repository", "object", "statusCheckRollup"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetCommitStatuses)
        withOperationName("get commit statuses")
      }

    fun getCommitStatusContext(
      repo: GHRepositoryCoordinates,
      commitHash: String,
      pageData: GraphQLRequestPagination
    ): GQLQuery<GraphQLConnectionDTO<GHCommitStatusRollupContextDTO>?> =
      GQLQuery.OptionalTraversedParsed(
        repo.serverPath.toGraphQLUrl(),
        GHGQLQueries.getRepositoryCommitStatusContexts,
        mapOf("repoOwner" to repo.repositoryPath.owner,
              "repoName" to repo.repositoryPath.repository,
              "oid" to commitHash) + pageData.asParameters(),
        ContextsConnection::class.java,
        "repository", "object", "statusCheckRollup", "contexts"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetCommitStatusContexts)
        withOperationName("get commit status contexts")
      }

    private class ContextsConnection(
      pageInfo: GraphQLCursorPageInfoDTO,
      nodes: List<GHCommitStatusRollupContextDTO> = listOf(),
    ) : GraphQLConnectionDTO<GHCommitStatusRollupContextDTO>(pageInfo, nodes)
  }

  object Comment {

    fun updateComment(server: GithubServerPath, commentId: String, newText: String): GQLQuery<GHComment> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.updateIssueComment,
        mapOf("id" to commentId,
              "body" to newText),
        GHComment::class.java,
        "updateIssueComment", "issueComment"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLUpdateIssueComment)
        withOperationName("update issue comment")
      }

    fun deleteComment(server: GithubServerPath, commentId: String): GQLQuery<Any?> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.deleteIssueComment,
        mapOf("id" to commentId),
        Any::class.java
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLDeleteIssueComment)
        withOperationName("delete issue comment")
      }

    fun addReaction(server: GithubServerPath, commentId: String, reaction: GHReactionContent): GQLQuery<GHReaction> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.addReaction,
        mapOf("id" to commentId,
              "reaction" to reaction),
        GHReaction::class.java,
        "addReaction", "reaction"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLAddReactionToComment)
        withOperationName("add reaction")
      }

    fun removeReaction(server: GithubServerPath, commentId: String, reaction: GHReactionContent): GQLQuery<GHReaction> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.removeReaction,
        mapOf("id" to commentId,
              "reaction" to reaction),
        GHReaction::class.java,
        "removeReaction", "reaction"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLRemoveReactionFromComment)
        withOperationName("remove reaction")
      }
  }

  object PullRequest {
    fun findOneId(repository: GHRepositoryCoordinates, number: Long): GQLQuery<GHPRIdentifier?> =
      GQLQuery.OptionalTraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.findPullRequestId,
        mapOf("repoOwner" to repository.repositoryPath.owner,
              "repoName" to repository.repositoryPath.repository,
              "number" to number),
        GHPRIdentifier::class.java,
        "repository", "pullRequest"
      ).apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType

        withOperation(GithubApiRequestOperation.GraphQLGetPullRequestId)
        withOperationName("get pull request id")
      }

    fun create(
      repository: GHRepositoryCoordinates,
      repositoryId: String,
      baseRefName: String,
      headRefName: String,
      title: String,
      body: String? = null,
      draft: Boolean? = false,
    ): GQLQuery<GHPullRequestShort> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.createPullRequest,
        mapOf("repositoryId" to repositoryId,
              "baseRefName" to baseRefName,
              "headRefName" to headRefName,
              "title" to title,
              "body" to body,
              "draft" to draft),
        GHPullRequestShort::class.java,
        "createPullRequest", "pullRequest"
      ).apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType

        withOperation(GithubApiRequestOperation.GraphQLCreatePullRequest)
        withOperationName("create pull request")
      }

    fun findOne(repository: GHRepositoryCoordinates, number: Long): GQLQuery<GHPullRequest?> =
      GQLQuery.OptionalTraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.findPullRequest,
        mapOf("repoOwner" to repository.repositoryPath.owner,
              "repoName" to repository.repositoryPath.repository,
              "number" to number),
        GHPullRequest::class.java,
        "repository", "pullRequest"
      ).apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType

        withOperation(GithubApiRequestOperation.GraphQLGetPullRequest)
        withOperationName("get pull request")
      }

    fun update(repository: GHRepositoryCoordinates, pullRequestId: String, title: String?, description: String?): GQLQuery<GHPullRequest> {
      val parameters = mutableMapOf<String, Any>("pullRequestId" to pullRequestId)
      if (title != null) parameters["title"] = title
      if (description != null) parameters["body"] = description

      return GQLQuery.TraversedParsed(repository.serverPath.toGraphQLUrl(), GHGQLQueries.updatePullRequest, parameters,
                                      GHPullRequest::class.java,
                                      "updatePullRequest", "pullRequest").apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType

        withOperation(GithubApiRequestOperation.GraphQLUpdatePullRequest)
        withOperationName("update pull request")
      }
    }

    fun markReadyForReview(repository: GHRepositoryCoordinates, pullRequestId: String): GQLQuery<Any?> =
      GQLQuery.Parsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.markPullRequestReadyForReview,
        mapOf<String, Any>("pullRequestId" to pullRequestId),
        Any::class.java
      ).apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType

        withOperation(GithubApiRequestOperation.GraphQLMarkPullRequestReadyForReview)
        withOperationName("mark pull request ready for review")
      }

    fun mergeabilityData(repository: GHRepositoryCoordinates, number: Long): GQLQuery<GHPullRequestMergeabilityData?> =
      GQLQuery.OptionalTraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestMergeabilityData,
        mapOf("repoOwner" to repository.repositoryPath.owner,
              "repoName" to repository.repositoryPath.repository,
              "number" to number),
        GHPullRequestMergeabilityData::class.java,
        "repository", "pullRequest"
      ).apply {
        acceptMimeType = "${GHSchemaPreview.CHECKS.mimeType},${GHSchemaPreview.PR_MERGE_INFO.mimeType}"

        withOperation(GithubApiRequestOperation.GraphQLGetMergeabilityData)
        withOperationName("get mergeability data")
      }

    fun search(server: GithubServerPath, query: String, pagination: GraphQLRequestPagination? = null): GQLQuery<GHGQLSearchQueryResponse<GHPullRequestShort>> =
      GQLQuery.Parsed(
        server.toGraphQLUrl(), GHGQLQueries.issueSearch,
        mapOf("query" to query,
              "pageSize" to pagination?.pageSize,
              "cursor" to pagination?.afterCursor),
        PRSearch::class.java
      ).apply {
        acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType

        withOperation(GithubApiRequestOperation.GraphQLSearchPullRequests)
        withOperationName("search issues")
      }

    internal fun metrics(repo: GHRepositoryCoordinates): GQLQuery<GHPullRequestMetrics> =
      GQLQuery.Parsed(
        repo.serverPath.toGraphQLUrl(), GHGQLQueries.metrics,
        MetricCountType.params(repo),
        GHPullRequestMetrics::class.java
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLPullRequestsMetrics)
        withOperationName("count pull requests")
      }

    private enum class MetricCountType(private val paramName: String) {
      ALL("allPRCountQuery"),
      OPEN("openPRCountQuery"),
      OPEN_AUTHORED("openAuthoredPRCountQuery"),
      OPEN_ASSIGNEE("openAssigneePRCountQuery"),
      OPEN_TO_BE_REVIEWED("openReviewAssignedPRCountQuery"),
      OPEN_REVIEWED("openReviewedPRCountQuery");

      private fun constructMetricQuery(repo: GHRepositoryCoordinates): String = GithubApiSearchQueryBuilder.searchQuery {
        if (this@MetricCountType != ALL) {
          term(QualifierName.state.createTerm(GithubIssueState.open.name))
        }

        term(QualifierName.type.createTerm(GithubIssueSearchType.pr.name))
        term(QualifierName.repo.createTerm(repo.repositoryPath.toString(showOwner = true)))

        when (this@MetricCountType) {
          OPEN_AUTHORED -> term(QualifierName.author.createTerm("@me"))
          OPEN_ASSIGNEE -> term(QualifierName.assignee.createTerm("@me"))
          OPEN_REVIEWED -> term(QualifierName.reviewedBy.createTerm("@me"))
          OPEN_TO_BE_REVIEWED -> term(QualifierName.reviewRequested.createTerm("@me"))
          else -> {}
        }
      }

      companion object {
        fun params(repo: GHRepositoryCoordinates): Map<String, String> =
          entries.associate { it.paramName to it.constructMetricQuery(repo) }
      }
    }

    private class PRSearch(search: SearchConnection<GHPullRequestShort>)
      : GHGQLSearchQueryResponse<GHPullRequestShort>(search)

    fun reviewThreads(
      repository: GHRepositoryCoordinates,
      number: Long,
      pagination: GraphQLRequestPagination? = null,
    ): GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestReviewThread>> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestReviewThreads,
        parameters(repository, number, pagination),
        ThreadsConnection::class.java, "repository", "pullRequest", "reviewThreads"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetReviewThreads)
        withOperationName("get review threads")
      }

    private class ThreadsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPullRequestReviewThread> = listOf())
      : GraphQLConnectionDTO<GHPullRequestReviewThread>(pageInfo, nodes)

    fun commits(
      repository: GHRepositoryCoordinates,
      number: Long,
      pagination: GraphQLRequestPagination? = null,
    ): GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestCommit>> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestCommits,
        parameters(repository, number, pagination),
        CommitsConnection::class.java, "repository", "pullRequest", "commits"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetPullRequestCommits)
        withOperationName("get pull request commits")
      }

    private class CommitsConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPullRequestCommit> = listOf())
      : GraphQLConnectionDTO<GHPullRequestCommit>(pageInfo, nodes)

    fun files(
      repository: GHRepositoryCoordinates,
      number: Long,
      pagination: GraphQLRequestPagination,
    ): GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestChangedFile>> =
      GQLQuery.TraversedParsed(
        repository.serverPath.toGraphQLUrl(), GHGQLQueries.pullRequestFiles,
        parameters(repository, number, pagination),
        FilesConnection::class.java, "repository", "pullRequest", "files"
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLGetPullRequestFiles)
        withOperationName("get pull request files")
      }

    private class FilesConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPullRequestChangedFile> = listOf()) :
      GraphQLConnectionDTO<GHPullRequestChangedFile>(pageInfo, nodes)

    fun markFileAsViewed(server: GithubServerPath, pullRequestId: String, path: String): GQLQuery<Unit> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.markFileAsViewed,
        mapOf("pullRequestId" to pullRequestId, "path" to path),
        Unit::class.java
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLMarkFileAsViewed)
        withOperationName("mark file as viewed")
      }

    fun unmarkFileAsViewed(server: GithubServerPath, pullRequestId: String, path: String): GQLQuery<Unit> =
      GQLQuery.TraversedParsed(
        server.toGraphQLUrl(), GHGQLQueries.unmarkFileAsViewed,
        mapOf("pullRequestId" to pullRequestId, "path" to path),
        Unit::class.java
      ).apply {
        withOperation(GithubApiRequestOperation.GraphQLUnmarkFileAsViewed)
        withOperationName("unmark file as viewed")
      }

    object Timeline {
      fun items(
        server: GithubServerPath, repoOwner: String, repoName: String, number: Long,
        pagination: GraphQLRequestPagination? = null,
      ): GQLQuery<GraphQLPagedResponseDataDTO<GHPRTimelineItem>> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.pullRequestTimeline,
          mapOf("repoOwner" to repoOwner,
                "repoName" to repoName,
                "number" to number,
                "pageSize" to pagination?.pageSize,
                "cursor" to pagination?.afterCursor,
                "since" to pagination?.since),
          TimelineConnection::class.java,
          "repository", "pullRequest", "timelineItems"
        ).apply {
          acceptMimeType = GHSchemaPreview.PR_DRAFT.mimeType

          withOperation(GithubApiRequestOperation.GraphQLGetPullRequestTimelineItems)
          withOperationName("get pull request timeline items")
        }

      private class TimelineConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GHPRTimelineItem> = listOf())
        : GraphQLConnectionDTO<GHPRTimelineItem>(pageInfo, nodes)
    }

    object Review {

      fun create(
        server: GithubServerPath, pullRequestId: String,
        event: GHPullRequestReviewEvent?, body: String?, commitSha: String?,
        threads: List<GHPullRequestDraftReviewThread>?,
      ): GQLQuery<GHPullRequestPendingReviewDTO> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.createReview,
          mapOf("pullRequestId" to pullRequestId,
                "event" to event,
                "commitOid" to commitSha,
                "threads" to threads,
                "body" to body),
          GHPullRequestPendingReviewDTO::class.java,
          "addPullRequestReview", "pullRequestReview"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLCreateReview)
          withOperationName("create review")
        }

      fun submit(server: GithubServerPath, reviewId: String, event: GHPullRequestReviewEvent, body: String?): GQLQuery<Any> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.submitReview,
          mapOf("reviewId" to reviewId,
                "event" to event,
                "body" to body),
          Any::class.java
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLSubmitReview)
          withOperationName("submit review")
        }

      fun updateBody(server: GithubServerPath, reviewId: String, newText: String): GQLQuery<GHPullRequestReview> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.updateReview,
          mapOf("reviewId" to reviewId,
                "body" to newText),
          GHPullRequestReview::class.java,
          "updatePullRequestReview", "pullRequestReview"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLUpdateReview)
          withOperationName("update review")
        }

      fun delete(server: GithubServerPath, reviewId: String): GQLQuery<Any> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.deleteReview,
          mapOf("reviewId" to reviewId),
          Any::class.java
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLDeleteReview)
          withOperationName("delete review")
        }

      fun pendingReviews(server: GithubServerPath, pullRequestId: String): GQLQuery<GraphQLNodesDTO<GHPullRequestPendingReviewDTO>> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.pendingReview,
          mapOf("pullRequestId" to pullRequestId),
          PendingReviewNodes::class.java,
          "node", "reviews"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLGetPendingReviews)
          withOperationName("get pending reviews")
        }

      private class PendingReviewNodes(nodes: List<GHPullRequestPendingReviewDTO> = listOf()) :
        GraphQLNodesDTO<GHPullRequestPendingReviewDTO>(nodes)

      fun addComment(
        server: GithubServerPath,
        reviewId: String,
        body: String, commitSha: String, fileName: String, diffLine: Int,
      ): GQLQuery<GHPullRequestReviewComment> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.addReviewComment,
          mapOf("reviewId" to reviewId,
                "body" to body,
                "commit" to commitSha,
                "file" to fileName,
                "position" to diffLine),
          GHPullRequestReviewComment::class.java,
          "addPullRequestReviewComment", "comment"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLCreateReviewCommentOnLine)
          withOperationName("create review comment on line")
        }

      fun addComment(
        server: GithubServerPath,
        reviewId: String,
        inReplyTo: String,
        body: String,
      ): GQLQuery<GHPullRequestReviewComment> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.addReviewComment,
          mapOf("reviewId" to reviewId,
                "inReplyTo" to inReplyTo,
                "body" to body),
          GHPullRequestReviewComment::class.java,
          "addPullRequestReviewComment", "comment"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLCreateReviewComment)
          withOperationName("create review comment")
        }

      fun deleteComment(server: GithubServerPath, commentId: String): GQLQuery<GHPullRequestPendingReviewDTO> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.deleteReviewComment,
          mapOf("id" to commentId),
          GHPullRequestPendingReviewDTO::class.java,
          "deletePullRequestReviewComment", "pullRequestReview"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLDeleteReviewComment)
          withOperationName("delete review comment")
        }

      fun updateComment(server: GithubServerPath, commentId: String, newText: String): GQLQuery<GHPullRequestReviewComment> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.updateReviewComment,
          mapOf("id" to commentId,
                "body" to newText),
          GHPullRequestReviewComment::class.java,
          "updatePullRequestReviewComment", "pullRequestReviewComment"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLUpdateReviewComment)
          withOperationName("update review comment")
        }

      fun addThread(
        server: GithubServerPath, reviewId: String,
        body: String, line: Int, side: Side, startLine: Int, fileName: String,
      ): GQLQuery<GHPullRequestReviewThread> {
        val params = mutableMapOf("pullRequestReviewId" to reviewId,
                                  "path" to fileName,
                                  "side" to side.name,
                                  "line" to line,
                                  "body" to body)
        if (startLine != line) {
          params["startSide"] = side.name
          params["startLine"] = startLine
        }

        return GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.addPullRequestReviewThread,
          params,
          GHPullRequestReviewThread::class.java,
          "addPullRequestReviewThread", "thread"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLCreateReviewThread)
          withOperationName("create review thread on line")
        }
      }

      fun resolveThread(server: GithubServerPath, threadId: String): GQLQuery<GHPullRequestReviewThread> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.resolveReviewThread,
          mapOf("threadId" to threadId),
          GHPullRequestReviewThread::class.java,
          "resolveReviewThread", "thread"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLResolveReviewThread)
          withOperationName("resolve review thread")
        }

      fun unresolveThread(server: GithubServerPath, threadId: String): GQLQuery<GHPullRequestReviewThread> =
        GQLQuery.TraversedParsed(
          server.toGraphQLUrl(), GHGQLQueries.unresolveReviewThread,
          mapOf("threadId" to threadId),
          GHPullRequestReviewThread::class.java,
          "unresolveReviewThread", "thread"
        ).apply {
          withOperation(GithubApiRequestOperation.GraphQLUnresolveReviewThread)
          withOperationName("unresolve review thread")
        }
    }
  }
}

private fun parameters(
  repository: GHRepositoryCoordinates,
  pullRequestNumber: Long,
  pagination: GraphQLRequestPagination?,
): Map<String, Any?> =
  mapOf(
    "repoOwner" to repository.repositoryPath.owner,
    "repoName" to repository.repositoryPath.repository,
    "number" to pullRequestNumber,
    "pageSize" to pagination?.pageSize,
    "cursor" to pagination?.afterCursor
  )