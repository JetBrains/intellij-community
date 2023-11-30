// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.SinceGitLab

// namespace can be null for some reason, so we need to parse paths manually
@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/project.graphql")
data class GitLabProjectDTO(
  val name: @Nls String,
  val nameWithNamespace: @Nls String,
  val path: @NlsSafe String,
  val fullPath: @NlsSafe String,
  val httpUrlToRepo: @NlsSafe String?,
  val sshUrlToRepo: @NlsSafe String?,
  val userPermissions: ProjectUserPermissions,
) {
  val ownerPath: @NlsSafe String = fullPath.split("/").dropLast(1).joinToString("/")

  /**
   * Corresponds to what GL calls ProjectPermissions. These are the permissions a *user* has while accessing a GL *project*.
   */
  data class ProjectUserPermissions(
    @SinceGitLab("12.6") val createSnippet: Boolean
  )
}