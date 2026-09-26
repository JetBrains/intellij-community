// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/label.graphql")
data class GitLabLabelGQLDTO(
  val title: @NlsSafe String,
  val color: String,
)