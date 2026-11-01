// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.api.sendAndAwait
import com.intellij.collaboration.util.resolveRelative
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiRestDTO
import org.jetbrains.plugins.gitlab.api.loadValue
import org.jetbrains.plugins.gitlab.api.projectApiUrl
import org.jetbrains.plugins.gitlab.api.withErrorStats
import org.jetbrains.plugins.gitlab.api.withQuery
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest

@SinceGitLab("8.9")
fun GitLabApi.Rest.getMRNotesAwardEmojiUri(projectId: String, mrIid: String, noteId: String): URI =
  projectApiUrl(projectId)
    .resolveRelative("merge_requests")
    .resolveRelative(mrIid)
    .resolveRelative("notes")
    .resolveRelative(noteId)
    .resolveRelative("award_emoji")

@SinceGitLab("8.9")
suspend fun GitLabApi.Rest.getMergeRequestNoteAwardEmoji(projectId: String, mrIid: String, noteId: String): List<GitLabAwardEmojiRestDTO> =
  ApiPageUtil.createPagesFlowByLinkHeader(getMRNotesAwardEmojiUri(projectId, mrIid, noteId)) { uri ->
    withErrorStats(GitLabApiRequestName.REST_GET_NOTE_AWARD_EMOJI) {
      request(uri).GET().build()
        .loadJsonList<GitLabAwardEmojiRestDTO>()
    }
  }.map { it.body() }.foldToList()

@SinceGitLab("8.9")
suspend fun GitLabApi.Rest.addAwardEmoji(
  projectId: String,
  mrIid: String,
  noteId: String,
  name: String,
): GitLabAwardEmojiRestDTO {
  val uri = getMRNotesAwardEmojiUri(projectId, mrIid, noteId).withQuery {
    "name" eq name
  }
  return request(uri).POST(HttpRequest.BodyPublishers.noBody()).build()
    .loadValue(GitLabApiRequestName.REST_CREATE_NOTE_AWARD_EMOJI)
}

@SinceGitLab("8.9")
suspend fun GitLabApi.Rest.deleteAwardEmoji(
  projectId: String,
  mrIid: String,
  noteId: String,
  awardId: String,
) {
  val uri = getMRNotesAwardEmojiUri(projectId, mrIid, noteId)
    .resolveRelative(awardId)
  val request = request(uri).DELETE().build()
  withErrorStats(GitLabApiRequestName.REST_DELETE_NOTE_AWARD_EMOJI) {
    sendAndAwait(request)
  }
}