// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

@GraphQLFragment("/graphql/fragment/mergeRequest.graphql")
class GitLabMergeRequestDTO(
  override val iid: String,
  val title: String,
  val description: String,
  val webUrl: String,
) : GitLabMergeRequestId