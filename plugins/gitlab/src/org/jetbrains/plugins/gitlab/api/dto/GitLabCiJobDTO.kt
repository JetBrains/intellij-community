// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus

@GraphQLFragment("/graphql/fragment/ciJob.graphql")
class GitLabCiJobDTO(
  val name: String,
  val status: GitLabCiJobStatus,
  val webPath: String
)