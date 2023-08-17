// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

fun getMergeRequestDraftNotesUri(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mrIid).resolveRelative("draft_notes")

suspend fun GitLabApi.Rest.updateDraftNote(project: GitLabProjectCoordinates,
                                           mrIid: String,
                                           noteId: Long,
                                           body: String)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).resolveRelative(noteId.toString())
  val request = request(uri)
    .withJsonContent()
    .PUT(jsonBodyPublisher(uri, mapOf(
      "note" to body
    )))
    .build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_UPDATE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

suspend fun GitLabApi.Rest.deleteDraftNote(project: GitLabProjectCoordinates,
                                           mrIid: String,
                                           noteId: Long)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).resolveRelative(noteId.toString())
  val request = request(uri).DELETE().build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_DELETE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

suspend fun GitLabApi.Rest.submitDraftNotes(project: GitLabProjectCoordinates,
                                            mrIid: String)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).resolveRelative("bulk_publish")
  val request = request(uri).POST(BodyPublishers.noBody()).build()
  return withErrorStats(project.serverPath, GitLabApiRequestName.REST_SUBMIT_DRAFT_NOTES) {
    sendAndAwaitCancellable(request)
  }
}
