// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

@SinceGitLab("15.9")
fun getMergeRequestDraftNotesUri(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("draft_notes")

private fun getSpecificMergeRequestDraftNoteUri(project: GitLabProjectCoordinates, mrIid: String, noteId: String): URI =
  getMergeRequestDraftNotesUri(project, mrIid).resolveRelative(noteId)

private fun URI.withParams(params: Map<String, String>): URI =
  withQuery(params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" })

@SinceGitLab("15.10")
suspend fun GitLabApi.Rest.updateDraftNote(project: GitLabProjectCoordinates,
                                           mrIid: String,
                                           noteId: String,
                                           position: GitLabMergeRequestDraftNoteRestDTO.Position,
                                           body: String)
  : HttpResponse<out Unit> {
  val params = mapOf(
    "note" to body,
  ) + createPositionParameters(position)

  val uri = getSpecificMergeRequestDraftNoteUri(project, mrIid, noteId).withParams(params)
  val request = request(uri).PUT(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_UPDATE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("15.9")
suspend fun GitLabApi.Rest.deleteDraftNote(project: GitLabProjectCoordinates,
                                           mrIid: String,
                                           noteId: Long)
  : HttpResponse<out Unit> {
  val uri = getSpecificMergeRequestDraftNoteUri(project, mrIid, noteId.toString())
  val request = request(uri).DELETE().build()
  return withErrorStats(GitLabApiRequestName.REST_DELETE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("15.11")
suspend fun GitLabApi.Rest.submitDraftNotes(project: GitLabProjectCoordinates,
                                            mrIid: String)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).resolveRelative("bulk_publish")
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_SUBMIT_DRAFT_NOTES) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("15.10")
suspend fun GitLabApi.Rest.submitSingleDraftNote(project: GitLabProjectCoordinates,
                                                 mrIid: String,
                                                 noteId: Long): HttpResponse<out Unit> {
  val uri = getSpecificMergeRequestDraftNoteUri(project, mrIid, noteId.toString()).resolveRelative("publish")
  val request = request(uri).PUT(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_SUBMIT_SINGLE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("16.3")
suspend fun GitLabApi.Rest.addDraftReplyNote(project: GitLabProjectCoordinates,
                                             mrIid: String,
                                             discussionId: String,
                                             body: String): HttpResponse<out GitLabMergeRequestDraftNoteRestDTO> {
  val params = listOfNotNull(
    "note" to body,
    "in_reply_to_discussion_id" to discussionId
  ).toMap()

  val uri = getMergeRequestDraftNotesUri(project, mrIid).withParams(params)
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_DRAFT_NOTE) {
    loadJsonValue(request)
  }
}

@SinceGitLab("15.10")
suspend fun GitLabApi.Rest.addDraftNote(project: GitLabProjectCoordinates,
                                        mrIid: String,
                                        @SinceGitLab("16.3")
                                        positionOrNull: GitLabDiffPositionInput?,
                                        body: String): HttpResponse<out GitLabMergeRequestDraftNoteRestDTO> {
  val params = mapOf(
    "note" to body,
  ) + (positionOrNull?.let(::createPositionParameters) ?: mapOf())

  val uri = getMergeRequestDraftNotesUri(project, mrIid).withParams(params)
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_DRAFT_NOTE) {
    loadJsonValue(request)
  }
}

private fun createPositionParameters(position: GitLabDiffPositionInput): Map<String, String> =
  listOfNotNull(
    "position[base_sha]" to position.baseSha,
    "position[head_sha]" to position.headSha,
    "position[start_sha]" to position.startSha,
    position.oldLine?.let { "position[old_line]" to it.toString() },
    position.newLine?.let { "position[new_line]" to it.toString() },
    position.paths.newPath?.let { "position[new_path]" to it },
    position.paths.oldPath?.let { "position[old_path]" to it },
    "position[position_type]" to "text",
  ).toMap()

private fun createPositionParameters(position: GitLabMergeRequestDraftNoteRestDTO.Position): Map<String, String> {
  val result = listOfNotNull(
    position.baseSha?.let { "position[base_sha]" to it },
    position.headSha?.let { "position[head_sha]" to it },
    position.startSha?.let { "position[start_sha]" to it },
    position.newPath?.let { "position[new_path]" to it },
    position.oldPath?.let { "position[old_path]" to it },
    position.oldLine?.let { "position[old_line]" to it.toString() },
    position.newLine?.let { "position[new_line]" to it.toString() },
    "position[position_type]" to position.positionType,
  ).toMap()

  // If there's no position info (just position type), don't pass it to GitLab.
  return if (result.size == 1) mapOf() else result
}
