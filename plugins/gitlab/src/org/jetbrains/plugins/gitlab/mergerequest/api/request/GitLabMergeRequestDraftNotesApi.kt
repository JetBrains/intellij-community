// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.net.URI
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers

fun getMergeRequestDraftNotesUri(project: GitLabProjectCoordinates, mr: GitLabMergeRequestId): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mr.iid).resolveRelative("draft_notes")

suspend fun GitLabApi.loadMergeRequestDraftNotes(uri: URI)
  : HttpResponse<out List<GitLabMergeRequestDraftNoteRestDTO>> {
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

suspend fun GitLabApi.updateDraftNote(project: GitLabProjectCoordinates, mr: GitLabMergeRequestId, noteId: Long, body: String)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mr).resolveRelative(noteId.toString())
  val request = request(uri)
    .withJsonContent()
    .PUT(jsonBodyPublisher(uri, mapOf(
      "note" to body
    )))
    .build()
  return sendAndAwaitCancellable(request, BodyHandlers.replacing(Unit))
}

suspend fun GitLabApi.deleteDraftNote(project: GitLabProjectCoordinates, mr: GitLabMergeRequestId, noteId: Long)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mr).resolveRelative(noteId.toString())
  val request = request(uri).DELETE().build()
  return sendAndAwaitCancellable(request, BodyHandlers.replacing(Unit))
}