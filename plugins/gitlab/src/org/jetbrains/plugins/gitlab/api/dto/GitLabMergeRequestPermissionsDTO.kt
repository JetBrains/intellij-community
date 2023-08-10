// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment

@GraphQLFragment("/graphql/fragment/mergeRequestPermissions.graphql")
data class GitLabMergeRequestPermissionsDTO(
  val canApprove: Boolean,
  val canMerge: Boolean,
  val createNote: Boolean,
  val updateMergeRequest: Boolean
)