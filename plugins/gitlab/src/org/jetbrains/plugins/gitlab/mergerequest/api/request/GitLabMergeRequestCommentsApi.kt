// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.data.orDefault
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.graphql.loadResponse
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.net.http.HttpResponse

suspend fun GitLabApi.GraphQL.loadMergeRequestDiscussions(project: GitLabProjectCoordinates,
                                                          mr: GitLabMergeRequestId,
                                                          pagination: GraphQLRequestPagination? = null)
  : GraphQLConnectionDTO<GitLabDiscussionDTO>? {
  val parameters = pagination.orDefault().asParameters() + mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mriid" to mr.iid
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.GET_MERGE_REQUEST_DISCUSSIONS, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.GET_MERGE_REQUEST_DISCUSSIONS) {
    loadResponse<DiscussionConnection>(request, "project", "mergeRequest", "discussions").body()
  }
}

private class DiscussionConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabDiscussionDTO>)
  : GraphQLConnectionDTO<GitLabDiscussionDTO>(pageInfo, nodes)

suspend fun GitLabApi.GraphQL.changeMergeRequestDiscussionResolve(
  project: GitLabProjectCoordinates,
  discussionId: String,
  resolved: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "discussionId" to discussionId,
    "resolved" to resolved
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE) {
    loadResponse<ResolveResult>(request, "discussionToggleResolve")
  }
}

private class ResolveResult(discussion: GitLabDiscussionDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>(errors) {
  override val value = discussion
}

suspend fun GitLabApi.GraphQL.updateNote(
  project: GitLabProjectCoordinates,
  noteId: String,
  newText: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<Unit>?> {
  val parameters = mapOf(
    "noteId" to noteId,
    "body" to newText
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.UPDATE_NOTE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.UPDATE_NOTE) {
    loadResponse<GitLabGraphQLMutationResultDTO.Empty>(request, "updateNote")
  }
}

suspend fun GitLabApi.GraphQL.deleteNote(
  project: GitLabProjectCoordinates,
  noteId: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<Unit>?> {
  val parameters = mapOf(
    "noteId" to noteId
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.DESTROY_NOTE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.DESTROY_NOTE) {
    loadResponse<GitLabGraphQLMutationResultDTO.Empty>(request, "destroyNote")
  }
}

suspend fun GitLabApi.GraphQL.addNote(
  project: GitLabProjectCoordinates,
  mergeRequestGid: String,
  body: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "noteableId" to mergeRequestGid,
    "body" to body
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.CREATE_NOTE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.CREATE_NOTE) {
    loadResponse<CreateNoteResult>(request, "createNote")
  }
}

suspend fun GitLabApi.GraphQL.addDiffNote(
  project: GitLabProjectCoordinates,
  mergeRequestGid: String,
  position: GitLabDiffPositionInput,
  body: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "noteableId" to mergeRequestGid,
    "position" to position,
    "body" to body
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.CREATE_DIFF_NOTE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.CREATE_DIFF_NOTE) {
    loadResponse<CreateNoteResult>(request, "createDiffNote")
  }
}

private class CreateNoteResult(note: NoteHolder?, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>(errors) {
  override val value = note?.discussion
}

private class NoteHolder(val discussion: GitLabDiscussionDTO)

suspend fun GitLabApi.GraphQL.createReplyNote(
  project: GitLabProjectCoordinates,
  mergeRequestGid: String,
  discussionId: String,
  body: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabNoteDTO>?> {
  val parameters = mapOf(
    "noteableId" to mergeRequestGid,
    "discussionId" to discussionId,
    "body" to body
  )
  val request = gitLabQuery(project.serverPath, GitLabGQLQuery.CREATE_REPLY_NOTE, parameters)
  return withErrorStats(project.serverPath, GitLabGQLQuery.CREATE_REPLY_NOTE) {
    loadResponse<CreateReplyNoteResult>(request, "createNote")
  }
}

private class CreateReplyNoteResult(note: GitLabNoteDTO?, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabNoteDTO>(errors) {
  override val value = note
}
