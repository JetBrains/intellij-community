// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel

@GraphQLFragment("graphql/fragment/member.graphql")
class GitLabMemberDTO(
  val id: String,
  val user: GitLabUserDTO,
  accessLevel: AccessLevel
) {
  val accessLevel: GitLabAccessLevel = parseAccessLevel(accessLevel.stringValue)

  class AccessLevel(val stringValue: String)

  companion object {
    private val logger: Logger = logger<GitLabMemberDTO>()

    private fun parseAccessLevel(accessLevel: String) = try {
      GitLabAccessLevel.valueOf(accessLevel)
    }
    catch (_: IllegalArgumentException) {
      logger.error("Unable to parse access level")
      GitLabAccessLevel.NO_ACCESS
    }
  }
}