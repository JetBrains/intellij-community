// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.auth.AccountDetails

@GraphQLFragment("graphql/fragment/user.graphql")
class GitLabUserDTO(
  val id: String,
  val username: String,
  override val name: String,
  override val avatarUrl: String?,
  val webUrl: String
) : AccountDetails