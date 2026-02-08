// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.data.orDefault
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiUriQueryBuilder
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteRestDTO
import org.jetbrains.plugins.gitlab.api.gitLabQuery
import org.jetbrains.plugins.gitlab.api.loadList
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.api.withQuery
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.LineRangeDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SinceGitLab("10.6")
suspend fun GitLabApi.Rest.loadMergeRequestDiscussions(
  project: GitLabProjectCoordinates,
  mrIid: String,
): HttpResponse<out List<GitLabDiscussionRestDTO>> {
  val uri = getMergeRequestDiscussionsUri(project, mrIid)
  return loadList(GitLabApiRequestName.REST_GET_MERGE_REQUEST_DISCUSSIONS, uri.toString())
}


@SinceGitLab("14.7")
suspend fun GitLabApi.GraphQL.loadMergeRequestCommits(
  project: GitLabProjectCoordinates,
  mrIid: String,
  pagination: GraphQLRequestPagination? = null
): GraphQLConnectionDTO<GitLabCommitDTO>? {
  val parameters = pagination.orDefault().asParameters() + mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mriid" to mrIid
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_MERGE_REQUEST_COMMITS, parameters)
  return withErrorStats(GitLabGQLQuery.GET_MERGE_REQUEST_COMMITS) {
    loadResponse<CommitConnection>(request, "project", "mergeRequest", "commits").body()
  }
}

private class CommitConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabCommitDTO>)
  : GraphQLConnectionDTO<GitLabCommitDTO>(pageInfo, nodes)

@SinceGitLab("10.6")
fun getMergeRequestDiscussionsUri(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("discussions")

@SinceGitLab("10.6")
suspend fun GitLabApi.Rest.addNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  body: String,
): HttpResponse<out GitLabDiscussionRestDTO> {
  val uri = getMergeRequestDiscussionsUri(project, mrIid).withQuery {
    "body" eq body
  }
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_MERGE_REQUEST_NOTE) {
    loadJsonValue(request)
  }
}

@SinceGitLab("13.2")
suspend fun GitLabApi.Rest.addDiffNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  position: GitLabDiffPositionInput,
  body: String,
): HttpResponse<out GitLabDiscussionRestDTO> {
  val uri = getMergeRequestDiscussionsUri(project, mrIid).withQuery {
    "body" eq body
    addDiffPositionParameters(position)
  }
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_MERGE_REQUEST_DIFF_NOTE) {
    loadJsonValue(request)
  }
}

@SinceGitLab("10.6")
suspend fun GitLabApi.Rest.createReplyNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  discussionId: String,
  body: String,
): HttpResponse<out GitLabNoteRestDTO> {
  val uri = getMergeRequestDiscussionsUri(project, mrIid)
    .resolveRelative(discussionId)
    .resolveRelative("notes")
    .withQuery {
      "body" eq body
    }
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_MERGE_REQUEST_DISCUSSION_NOTE) {
    loadJsonValue(request)
  }
}

@SinceGitLab("10.6")
suspend fun GitLabApi.Rest.updateNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  discussionId: String,
  noteId: String,
  body: String,
): HttpResponse<out GitLabNoteRestDTO> {
  val uri = getMergeRequestDiscussionsUri(project, mrIid)
    .resolveRelative(discussionId)
    .resolveRelative("notes")
    .resolveRelative(noteId)
    .withQuery {
      "body" eq body
    }
  val request = request(uri).PUT(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_UPDATE_MERGE_REQUEST_DISCUSSION_NOTE) {
    loadJsonValue(request)
  }
}

@SinceGitLab("10.6")
suspend fun GitLabApi.Rest.deleteNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  discussionId: String,
  noteId: String,
): HttpResponse<out Unit> {
  val uri = getMergeRequestDiscussionsUri(project, mrIid)
    .resolveRelative(discussionId)
    .resolveRelative("notes")
    .resolveRelative(noteId)
  val request = request(uri).DELETE().build()
  return withErrorStats(GitLabApiRequestName.REST_DELETE_MERGE_REQUEST_DISCUSSION_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("10.8")
suspend fun GitLabApi.Rest.changeMergeRequestDiscussionResolve(
  project: GitLabProjectCoordinates,
  mrIid: String,
  discussionId: String,
  resolved: Boolean,
): HttpResponse<out GitLabDiscussionRestDTO> {
  val uri = getMergeRequestDiscussionsUri(project, mrIid)
    .resolveRelative(discussionId)
    .withQuery {
      "resolved" eq resolved
    }
  val request = request(uri).PUT(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_UPDATE_MERGE_REQUEST_DISCUSSION) {
    loadJsonValue(request)
  }
}


/**
 * Extension function to add diff position parameters to the query builder.
 *
 * This function converts a [GitLabDiffPositionInput] to query parameters in the format
 * expected by GitLab REST API for creating notes and draft notes on diffs.
 */
internal fun GitLabApiUriQueryBuilder.addDiffPositionParameters(position: GitLabDiffPositionInput) {
  "position" {
    "base_sha" eq position.baseSha
    "head_sha" eq position.headSha
    "start_sha" eq position.startSha
    "old_line" eq position.oldLine
    "new_line" eq position.newLine
    "new_path" eq position.paths.newPath
    "old_path" eq position.paths.oldPath
    "position_type" eq "text"
    position.lineRange?.let { lineRange ->
      addLineRangeParameters(lineRange)
    }
  }
}

internal fun GitLabApiUriQueryBuilder.addLineRangeParameters(lineRange: LineRangeDTO) {
  "line_range" {
    "start" {
      "line_code" eq lineRange.start.lineCode
      "type" eq lineRange.start.type
      "old_line" eq lineRange.start.oldLine
      "new_line" eq lineRange.start.newLine
    }
    "end" {
      "line_code" eq lineRange.end.lineCode
      "type" eq lineRange.end.type
      "old_line" eq lineRange.end.oldLine
      "new_line" eq lineRange.end.newLine
    }
  }
}


