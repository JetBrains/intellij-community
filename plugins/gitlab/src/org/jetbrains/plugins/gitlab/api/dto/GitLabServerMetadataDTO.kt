// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("12.0", note = "As GraphQL DTO")
@SinceGitLab("15.2", note = "As REST DTO")
@GraphQLFragment("/graphql/fragment/metadata.graphql")
data class GitLabServerMetadataDTO(
  val version: String,
  val revision: String,
  //val kas: Any, - Kubernetes data
  @SinceGitLab("15.6", note = "For both")
  val enterprise: Boolean?
)
