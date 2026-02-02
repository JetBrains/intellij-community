// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiRestDTO
import org.jetbrains.plugins.gitlab.api.restApiUri
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.api.withParams
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SinceGitLab("8.9")
fun getMRNotesAwardEmojiUri(project: GitLabProjectCoordinates, mrIid: String, noteId: String): URI =
  project.restApiUri
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("notes")
    .resolveRelative(noteId)
    .resolveRelative("award_emoji")

@SinceGitLab("8.9")
suspend fun GitLabApi.Rest.addAwardEmoji(
  project: GitLabProjectCoordinates,
  mrIid: String,
  noteId: String,
  name: String,
): HttpResponse<out GitLabAwardEmojiRestDTO> {
  val params = mapOf("name" to name)
  val uri = getMRNotesAwardEmojiUri(project, mrIid, noteId)
    .withParams(params)
  val request = request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
  return withErrorStats(GitLabApiRequestName.REST_CREATE_NOTE_AWARD_EMOJI) {
    loadJsonValue(request)
  }
}

@SinceGitLab("8.9")
suspend fun GitLabApi.Rest.deleteAwardEmoji(
  project: GitLabProjectCoordinates,
  mrIid: String,
  noteId: String,
  awardId: String,
): HttpResponse<out Unit> {
  val uri = getMRNotesAwardEmojiUri(project, mrIid, noteId)
    .resolveRelative(awardId)
  val request = request(uri).DELETE().build()
  return withErrorStats(GitLabApiRequestName.REST_DELETE_NOTE_AWARD_EMOJI) {
    sendAndAwaitCancellable(request)
  }
}