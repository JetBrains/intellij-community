// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.ui.codereview.user.CodeReviewUser
import org.jetbrains.annotations.Nls

@GraphQLFragment("/graphql/fragment/actor.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = GHActor.Unknown::class)
@JsonSubTypes(
  JsonSubTypes.Type(name = "User", value = GHUser::class),
  JsonSubTypes.Type(name = "Bot", value = GHBot::class),
  JsonSubTypes.Type(name = "Mannequin", value = GHMannequin::class),
  JsonSubTypes.Type(name = "Organization", value = GHOrganization::class),
  JsonSubTypes.Type(name = "EnterpriseUserAccount", value = GHEnterpriseUserAccount::class),
)
interface GHActor : CodeReviewUser {
  val id: String
  val login: String
  val url: String
  override val avatarUrl: String

  fun getPresentableName(): @Nls String

  class Unknown(
    override val id: String,
    override val login: String,
    override val url: String,
    override val avatarUrl: String,
  ) : GHActor {
    override fun getPresentableName(): @Nls String = login //NON-NLS
  }
}