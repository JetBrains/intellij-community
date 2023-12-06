// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("13.11")
@GraphQLFragment("graphql/fragment/userReviewer.graphql")
class GitLabReviewerDTO(
  id: String,
  username: @NlsSafe String,
  name: @NlsSafe String,
  avatarUrl: String?,
  webUrl: String,
  val mergeRequestInteraction: MergeRequestInteraction?
) : GitLabUserDTO(id, username, name, avatarUrl, webUrl) {

  data class MergeRequestInteraction(
    val approved: Boolean,
    val reviewed: Boolean
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as GitLabReviewerDTO

    return mergeRequestInteraction == other.mergeRequestInteraction
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + (mergeRequestInteraction?.hashCode() ?: 0)
    return result
  }
}