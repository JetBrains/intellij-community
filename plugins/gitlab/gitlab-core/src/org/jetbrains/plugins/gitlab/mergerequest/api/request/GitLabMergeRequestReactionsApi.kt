// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.request

import com.intellij.collaboration.api.graphql.loadResponse
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import java.net.http.HttpResponse

@SinceGitLab("13.2")
suspend fun GitLabApi.GraphQL.awardEmojiToggle(
  noteId: String,
  name: String
): HttpResponse<out AwardEmojiTogglePayload> {
  val parameters = mapOf(
    "noteId" to noteId,
    "name" to name
  )
  val request = gitLabQuery(GitLabGQLQuery.AWARD_EMOJI_TOGGLE, parameters)
  return withErrorStats(GitLabGQLQuery.AWARD_EMOJI_TOGGLE) {
    loadResponse<AwardEmojiTogglePayload>(request, "awardEmojiToggle")
  }
}

class AwardEmojiTogglePayload(
  val awardEmoji: GitLabAwardEmojiDTO?,
  val toggledOn: Boolean,
  errors: List<String>?
) : GitLabGraphQLMutationResultDTO<GitLabAwardEmojiDTO>(errors) {
  override val value = awardEmoji
}