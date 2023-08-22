// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import java.util.*

@SinceGitLab("12.1")
@GraphQLFragment("/graphql/fragment/glCommit.graphql")
data class GitLabCommitDTO(
  val sha: String,
  @SinceGitLab("13.7") val shortId: String,
  @SinceGitLab("14.5") val fullTitle: @NlsSafe String?,
  val description: @NlsSafe String?,
  val author: GitLabUserDTO?,
  @SinceGitLab("12.5") val authorName: String,
  val authoredDate: Date
)