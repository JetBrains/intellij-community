// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.annotations.ApiStatus.NonExtendable

@GraphQLFragment("/graphql/fragment/repositoryOwnerName.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = GHRepositoryOwnerName::class)
@JsonSubTypes(
  JsonSubTypes.Type(name = "User", value = GHRepositoryOwnerName.User::class),
  JsonSubTypes.Type(name = "Organization", value = GHRepositoryOwnerName.Organization::class)
)
@NonExtendable
open class GHRepositoryOwnerName(
  val login: String,
) {
  class User(login: String) : GHRepositoryOwnerName(login)
  class Organization(login: String) : GHRepositoryOwnerName(login)
}