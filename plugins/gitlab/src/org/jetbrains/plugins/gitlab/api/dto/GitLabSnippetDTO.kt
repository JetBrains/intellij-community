// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import java.util.*

@SinceGitLab("12.6")
@GraphQLFragment("graphql/fragment/snippet.graphql")
class GitLabSnippetDTO(
  val id: String,
  val createdAt: Date,
  val author: GitLabUserDTO,
  val webUrl: @NlsSafe String
)