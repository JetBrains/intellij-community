// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHBot
import org.jetbrains.plugins.github.api.data.GHMannequin
import org.jetbrains.plugins.github.api.data.GHUser

@GraphQLFragment("/graphql/fragment/pullRequestReviewer.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = GHPullRequestRequestedReviewer.Unknown::class)
@JsonSubTypes(
  JsonSubTypes.Type(name = "User", value = GHUser::class),
  JsonSubTypes.Type(name = "Bot", value = GHBot::class),
  JsonSubTypes.Type(name = "Mannequin", value = GHMannequin::class),
  JsonSubTypes.Type(name = "Team", value = GHTeam::class)
)
interface GHPullRequestRequestedReviewer {
  val id: String
  val shortName: String
  val url: String
  val avatarUrl: String
  val name: String?

  fun getPresentableName(): @NlsSafe String = name ?: shortName

  class Unknown(
    override val id: String,
    override val url: String,
    override val avatarUrl: String,
    override val name: String?,
  ) : GHPullRequestRequestedReviewer {
    override val shortName: String = id
  }
}