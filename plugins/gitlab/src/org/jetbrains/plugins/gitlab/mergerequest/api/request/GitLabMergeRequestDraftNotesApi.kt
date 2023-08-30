// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

@SinceGitLab("15.9")
fun getMergeRequestDraftNotesUri(project: GitLabProjectCoordinates, mrIid: String): URI =
  project.restApiUri.resolveRelative("merge_requests").resolveRelative(mrIid).resolveRelative("draft_notes")

@SinceGitLab("15.10")
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
  return withErrorStats(GitLabApiRequestName.REST_UPDATE_DRAFT_NOTE) {
    sendAndAwaitCancellable(request)
  }
}

@SinceGitLab("15.9")
suspend fun GitLabApi.Rest.deleteDraftNote(project: GitLabProjectCoordinates,
                                           mrIid: String,
                                           noteId: Long)
  : HttpResponse<out Unit> {
  val uri = getMergeRequestDraftNotesUri(project, mrIid).resolveRelative(noteId.toString())
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
