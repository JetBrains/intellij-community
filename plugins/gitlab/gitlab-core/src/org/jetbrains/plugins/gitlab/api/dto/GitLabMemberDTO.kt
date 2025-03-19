// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.data.GitLabAccessLevel

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
    val codec = jsonParser.codec
    val node: JsonNode = codec.readTree(jsonParser)

    return if (node.isEmpty) null
    else {
      //codec.treeToValue(node, GitLabMemberDTO::class.java) leads to SOE
      val id: String = node["id"].asText()
      val user: GitLabUserDTO = codec.treeToValue(node["user"], GitLabUserDTO::class.java)
      val accessLevel = node["accessLevel"]["stringValue"].asText().let(::parseAccessLevel)
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