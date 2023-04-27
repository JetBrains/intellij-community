// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.openapi.util.NlsSafe

@GraphQLFragment("graphql/fragment/user.graphql")
data class GitLabUserDTO(
  val id: String,
  val username: @NlsSafe String,
  override val name: @NlsSafe String,
  override val avatarUrl: String?,
  val webUrl: String
) : AccountDetails {

  companion object {
    fun fromRestDTO(dto: GitLabUserRestDTO): GitLabUserDTO = GitLabUserDTO(
      id = "gid://gitlab/User/${dto.id}",
      username = dto.username,
      name = dto.name,
      avatarUrl = dto.avatarUrl?.removePrefix("https://gitlab.com"),
      webUrl = dto.webUrl
    )
  }
}