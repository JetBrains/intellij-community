// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.data.asParameters
import com.intellij.collaboration.api.data.orDefault
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.net.http.HttpResponse

suspend fun GitLabApi.loadMergeRequestDiscussions(project: GitLabProjectCoordinates,
                                                  mr: GitLabMergeRequestId,
                                                  pagination: GraphQLRequestPagination? = null)
  : GraphQLConnectionDTO<GitLabDiscussionDTO>? {
  val parameters = pagination.orDefault().asParameters() + mapOf(
    "projectId" to project.projectPath.fullPath(),
    "mriid" to mr.iid
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.getMergeRequestDiscussions, parameters)
  return loadGQLResponse(request, DiscussionConnection::class.java, "project", "mergeRequest", "discussions").body()
}

private class DiscussionConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabDiscussionDTO>)
  : GraphQLConnectionDTO<GitLabDiscussionDTO>(pageInfo, nodes)

suspend fun GitLabApi.changeMergeRequestDiscussionResolve(
  project: GitLabProjectCoordinates,
  discussionId: String,
  resolved: Boolean
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "discussionId" to discussionId,
    "resolved" to resolved
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.toggleMergeRequestDiscussionResolve, parameters)
  return loadGQLResponse(request, ResolveResult::class.java, "discussionToggleResolve")
}

private class ResolveResult(discussion: GitLabDiscussionDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>(errors) {
  override val value = discussion
}

suspend fun GitLabApi.updateNote(
  project: GitLabProjectCoordinates,
  noteId: String,
  newText: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<Unit>?> {
  val parameters = mapOf(
    "noteId" to noteId,
    "body" to newText
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.updateNote, parameters)
  return loadGQLResponse(request, GitLabGraphQLMutationResultDTO.Empty::class.java, "updateNote")
}

suspend fun GitLabApi.deleteNote(
  project: GitLabProjectCoordinates,
  noteId: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<Unit>?> {
  val parameters = mapOf(
    "noteId" to noteId
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.destroyNote, parameters)
  return loadGQLResponse(request, GitLabGraphQLMutationResultDTO.Empty::class.java, "destroyNote")
}

suspend fun GitLabApi.addNote(
  project: GitLabProjectCoordinates,
  mergeRequestGid: String,
  body: String
): HttpResponse<out GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>?> {
  val parameters = mapOf(
    "noteableId" to mergeRequestGid,
    "body" to body
  )
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.createNote, parameters)
  return loadGQLResponse(request, CreateNoteResult::class.java, "createNote")
}

suspend fun GitLabApi.addDiffNote(
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
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.createDiffNote, parameters)
  return loadGQLResponse(request, CreateNoteResult::class.java, "createDiffNote")
}

private class CreateNoteResult(note: NoteHolder?, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabDiscussionDTO>(errors) {
  override val value = note?.discussion
}

private class NoteHolder(val discussion: GitLabDiscussionDTO)

suspend fun GitLabApi.createReplyNote(
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
  val request = gqlQuery(project.serverPath.gqlApiUri, GitLabGQLQueries.createReplyNote, parameters)
  return loadGQLResponse(request, CreateReplyNoteResult::class.java, "createNote")
}

private class CreateReplyNoteResult(note: GitLabNoteDTO, errors: List<String>?)
  : GitLabGraphQLMutationResultDTO<GitLabNoteDTO>(errors) {
  override val value = note
}