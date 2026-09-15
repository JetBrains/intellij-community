// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.deser.std.StdDeserializer

@SinceGitLab("13.1")
@GraphQLFragment("graphql/fragment/member.graphql")
@JsonDeserialize(using = GitLabMemberDTODeserializer::class)
class GitLabMemberDTO(
  val id: String,
  val user: GitLabUserDTO,
  val accessLevel: GitLabAccessLevel
)

class GitLabMemberDTODeserializer : StdDeserializer<GitLabMemberDTO?>(GitLabMemberDTO::class.java) {
  override fun deserialize(jsonParser: JsonParser, context: DeserializationContext): GitLabMemberDTO? {
    val node: JsonNode = context.readTree(jsonParser)

    return if (node.isEmpty) null
    else {
      //context.readTreeAsValue(node, GitLabMemberDTO::class.java) leads to SOE
      val id: String = node["id"].asString()
      val user: GitLabUserDTO = context.readTreeAsValue(node["user"], GitLabUserDTO::class.java)
      val accessLevel = node["accessLevel"]["stringValue"].asString().let(::parseAccessLevel)
      GitLabMemberDTO(id, user, accessLevel)
    }
  }

  private fun parseAccessLevel(accessLevel: String) = try {
    GitLabAccessLevel.valueOf(accessLevel)
  }
  catch (_: IllegalArgumentException) {
    LOG.error("Unable to parse access level")
    GitLabAccessLevel.NO_ACCESS
  }

  companion object {
    private val LOG: Logger = logger<GitLabMemberDTO>()
  }
}