// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.openapi.util.NlsSafe
import java.net.URL

@GraphQLFragment("graphql/fragment/user.graphql")
open class GitLabUserDTO(
  val id: String,
  val username: @NlsSafe String,
  override val name: @NlsSafe String,
  override val avatarUrl: String?,
  val webUrl: String
) : AccountDetails {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitLabUserDTO

    if (id != other.id) return false
    if (username != other.username) return false
    if (name != other.name) return false
    if (avatarUrl != other.avatarUrl) return false
    if (webUrl != other.webUrl) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + username.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + (avatarUrl?.hashCode() ?: 0)
    result = 31 * result + webUrl.hashCode()
    return result
  }

  companion object {
    fun fromRestDTO(dto: GitLabUserRestDTO): GitLabUserDTO {
      val url = URL(dto.webUrl)
      val serverId = url.host.split(".").dropLast(1).joinToString(".")

      val server = dto.avatarUrl?.let {
        URL(it)
      }

      return GitLabUserDTO(
        id = "gid://$serverId/User/${dto.id}",
        username = dto.username,
        name = dto.name,
        avatarUrl = server?.path,
        webUrl = dto.webUrl
      )
    }
  }
}