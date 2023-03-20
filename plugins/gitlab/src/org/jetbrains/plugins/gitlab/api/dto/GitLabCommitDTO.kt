// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import java.util.*

@GraphQLFragment("/graphql/fragment/glCommit.graphql")
class GitLabCommitDTO(
  val sha: String,
  val shortId: String,
  val title: @NlsSafe String?,
  val description: @NlsSafe String?,
  val author: GitLabUserDTO,
  val authoredDate: Date
)