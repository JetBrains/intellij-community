// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiUriQueryBuilder
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.api.withQuery
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
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

@SinceGitLab("15.10")
suspend fun GitLabApi.Rest.updateDraftNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  noteId: String,
  position: GitLabMergeRequestDraftNoteRestDTO.Position,
  body: String,
)
  : HttpResponse<out Unit> {
  val uri = getSpecificMergeRequestDraftNoteUri(project, mrIid, noteId).withQuery {
    "note" eq body
    addDraftNotePositionParameters(position) // have to pass the existing position, otherwise it is reset to null
  }
  val request = request(uri).PUT(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_UPDATE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("15.9")
suspend fun GitLabApi.Rest.deleteDraftNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  noteId: Long,
)
  : HttpResponse<out Unit> {
  val uri = getSpecificMergeRequestDraftNoteUri(project, mrIid, noteId.toString())
  val request = request(uri).DELETE().build()
  return withErrorStats(GitLabApiRequestName.REST_DELETE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("15.11")
suspend fun GitLabApi.Rest.submitDraftNotes(
  project: GitLabProjectCoordinates,
  mrIid: String,
)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).resolveRelative("bulk_publish")
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_SUBMIT_DRAFT_NOTES) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("15.10")
suspend fun GitLabApi.Rest.submitSingleDraftNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  noteId: Long,
): HttpResponse<out Unit> {
  val uri = getSpecificMergeRequestDraftNoteUri(project, mrIid, noteId.toString()).resolveRelative("publish")
  val request = request(uri).PUT(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_SUBMIT_SINGLE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("16.3")
suspend fun GitLabApi.Rest.addDraftReplyNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  discussionId: String,
  body: String,
): HttpResponse<out GitLabMergeRequestDraftNoteRestDTO> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).withQuery {
    "note" eq body
    "in_reply_to_discussion_id" eq discussionId
  }
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_DRAFT_NOTE) {
    loadJsonValue(request)
  }
}

@SinceGitLab("15.10")
suspend fun GitLabApi.Rest.addDraftNote(
  project: GitLabProjectCoordinates,
  mrIid: String,
  @SinceGitLab("16.3")
  positionOrNull: GitLabDiffPositionInput?,
  body: String,
): HttpResponse<out GitLabMergeRequestDraftNoteRestDTO> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).withQuery {
    "note" eq body
    positionOrNull?.let { addDiffPositionParameters(it) }
  }
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_DRAFT_NOTE) {
    loadJsonValue(request)
  }
}

private fun GitLabApiUriQueryBuilder.addDraftNotePositionParameters(position: GitLabMergeRequestDraftNoteRestDTO.Position) {
  // If there's no position info (just position type), don't pass it to GitLab.
  if (position.baseSha == null && position.headSha == null && position.startSha == null &&
      position.newPath == null && position.oldPath == null &&
      position.oldLine == null && position.newLine == null && position.lineRange == null) {
    return
  }

  "position" {
    "base_sha" eq position.baseSha
    "head_sha" eq position.headSha
    "start_sha" eq position.startSha
    "new_path" eq position.newPath
    "old_path" eq position.oldPath
    "old_line" eq position.oldLine
    "new_line" eq position.newLine
    "position_type" eq position.positionType
    position.lineRange?.let { lineRange ->
      addLineRangeParameters(lineRange)
    }
  }
}
