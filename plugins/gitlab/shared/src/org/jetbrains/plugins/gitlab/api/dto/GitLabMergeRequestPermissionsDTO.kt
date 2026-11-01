// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/mergeRequestPermissions.graphql")
data class GitLabMergeRequestPermissionsDTO(
  @SinceGitLab("15.9") val canApprove: Boolean?,
  @SinceGitLab("13.4") val canMerge: Boolean,
  val createNote: Boolean,
  val updateMergeRequest: Boolean
)