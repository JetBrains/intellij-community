// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.data.orDefault
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import java.net.http.HttpResponse

@SinceGitLab("12.3")
suspend fun GitLabApi.GraphQL.loadMergeRequestDiscussions(project: GitLabProjectCoordinates,
                                                          mrIid: String,
                                                          pagination: GraphQLRequestPagination? = null)
  : GraphQLConnectionDTO<GitLabDiscussionDTO>? {
  val parameters = pagination.orDefault().asParameters() + mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mriid" to mrIid
  )
  val request = gitLabQuery(GitLabGQLQuery.GET_MERGE_REQUEST_DISCUSSIONS, parameters)
  return withErrorStats(GitLabGQLQuery.GET_MERGE_REQUEST_DISCUSSIONS) {
    loadResponse<DiscussionConnection>(request, "project", "mergeRequest", "discussions").body()
  }
}

private class DiscussionConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabDiscussionDTO>)
  : GraphQLConnectionDTO<GitLabDiscussionDTO>(pageInfo, nodes)

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

@SinceGitLab("13.1", note = "Different ID type until 13.6, should work")
suspend fun GitLabApi.GraphQL.changeMergeRequestDiscussionResolve(
  discussionId: String,
  resolved: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "discussionId" to discussionId,
    "resolved" to resolved
  )
  val request = gitLabQuery(GitLabGQLQuery.TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE, parameters)
  return withErrorStats(GitLabGQLQuery.TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE) {
    loadResponse<ResolveResult>(request, "discussionToggleResolve")
  }
}

private class ResolveResult(discussion: GitLabDiscussionDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>(errors) {
  override val value = discussion
}

@SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
suspend fun GitLabApi.GraphQL.updateNote(
  noteId: String,
  newText: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<Unit>?> {
  val parameters = mapOf(
    "noteId" to noteId,
    "body" to newText
  )
  val request = gitLabQuery(GitLabGQLQuery.UPDATE_NOTE, parameters)
  return withErrorStats(GitLabGQLQuery.UPDATE_NOTE) {
    loadResponse<GitLabGraphQLMutationResultDTO.Empty>(request, "updateNote")
  }
}

@SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
suspend fun GitLabApi.GraphQL.deleteNote(
  noteId: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<Unit>?> {
  val parameters = mapOf(
    "noteId" to noteId
  )
  val request = gitLabQuery(GitLabGQLQuery.DESTROY_NOTE, parameters)
  return withErrorStats(GitLabGQLQuery.DESTROY_NOTE) {
    loadResponse<GitLabGraphQLMutationResultDTO.Empty>(request, "destroyNote")
  }
}

@SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
suspend fun GitLabApi.GraphQL.addNote(
  mergeRequestGid: String,
  body: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "noteableId" to mergeRequestGid,
    "body" to body
  )
  val request = gitLabQuery(GitLabGQLQuery.CREATE_NOTE, parameters)
  return withErrorStats(GitLabGQLQuery.CREATE_NOTE) {
    loadResponse<CreateNoteResult>(request, "createNote")
  }
}

@SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
suspend fun GitLabApi.GraphQL.addDiffNote(
  mergeRequestGid: String,
  position: GitLabDiffPositionInput,
  body: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "noteableId" to mergeRequestGid,
    "position" to position,
    "body" to body
  )
  val request = gitLabQuery(GitLabGQLQuery.CREATE_DIFF_NOTE, parameters)
  return withErrorStats(GitLabGQLQuery.CREATE_DIFF_NOTE) {
    loadResponse<CreateNoteResult>(request, "createDiffNote")
  }
}

private class CreateNoteResult(note: NoteHolder?, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>(errors) {
  override val value = note?.discussion
}

private class NoteHolder(val discussion: GitLabDiscussionDTO)

@SinceGitLab("12.1", note = "Different ID type until 13.6, should work")
suspend fun GitLabApi.GraphQL.createReplyNote(
  mergeRequestGid: String,
  discussionId: String,
  body: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabNoteDTO>?> {
  val parameters = mapOf(
    "noteableId" to mergeRequestGid,
    "discussionId" to discussionId,
    "body" to body
  )
  val request = gitLabQuery(GitLabGQLQuery.CREATE_REPLY_NOTE, parameters)
  return withErrorStats(GitLabGQLQuery.CREATE_REPLY_NOTE) {
    loadResponse<CreateReplyNoteResult>(request, "createNote")
  }
}

private class CreateReplyNoteResult(note: GitLabNoteDTO?, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabNoteDTO>(errors) {
  override val value = note
}
