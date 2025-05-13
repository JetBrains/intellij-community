// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.GitLabGid
import org.jetbrains.plugins.gitlab.api.GitLabGidData

@ApiStatus.Internal
interface WithGitLabNamespace {
  val id: GitLabGid?

  val fullName: @NlsSafe String
  val fullPath: String
}

@ApiStatus.Internal
@GraphQLFragment("/graphql/fragment/namespace.graphql")
data class GitLabNamespaceDTO(
  override val fullName: @NlsSafe String,
  override val fullPath: String,
) : WithGitLabNamespace {
  @JsonIgnore
  override val id: GitLabGid? = null
}

@ApiStatus.Internal
@GraphQLFragment("/graphql/fragment/group.graphql")
data class GitLabGroupDTO(
  @JsonProperty("id")
  private val _id: String,

  override val fullName: @NlsSafe String,
  override val fullPath: String,
  val userPermissions: UserPermissions,
) : WithGitLabNamespace {
  @JsonIgnore
  override val id: GitLabGid = GitLabGidData(_id)

  data class UserPermissions(
    val createProjects: Boolean,
  )
}
