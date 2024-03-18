// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.ui.codereview.user.CodeReviewUser
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import java.net.URL

@SinceGitLab("12.0")
@GraphQLFragment("graphql/fragment/user.graphql")
open class GitLabUserDTO(
  @SinceGitLab("13.0") val id: String,
  val username: @NlsSafe String,
  override val name: @NlsSafe String,
  override val avatarUrl: String?,
  val webUrl: String
) : AccountDetails, CodeReviewUser {

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
      // Basing ID on serverId doesn't work when the server is an IP, honestly not even sure what GL setting the name is based on.
      // For locally setup gitlab servers this is 'gitlab' as well, but not sure if it can be set.
      // val url = URL(dto.webUrl)
      // val serverId = url.host.split(".").dropLast(1).joinToString(".")

      val server = dto.avatarUrl?.let {
        URL(it)
      }

      // Assuming all gitlab servers use 'gitlab' as server ID. Seems to be dictated
      // by a global 'GlobalID.app' setting for the 'globalid' library for RoR.
      return GitLabUserDTO(
        id = "gid://gitlab/User/${dto.id}",
        username = dto.username,
        name = dto.name,
        avatarUrl = server?.path,
        webUrl = dto.webUrl
      )
    }
  }
}