// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabGid
import org.jetbrains.plugins.gitlab.api.GitLabGidData
import org.jetbrains.plugins.gitlab.api.SinceGitLab

// namespace can be null for some reason, so we need to parse paths manually
@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/project.graphql")
data class GitLabProjectDTO(
  @JsonProperty("id") private val _id: String,
  val name: @Nls String,
  val nameWithNamespace: @Nls String,
  val path: @NlsSafe String,
  val fullPath: @NlsSafe String,
  val httpUrlToRepo: @NlsSafe String?,
  val sshUrlToRepo: @NlsSafe String?,
  val onlyAllowMergeIfAllDiscussionsAreResolved: Boolean,
  val onlyAllowMergeIfPipelineSucceeds: Boolean,
  @SinceGitLab("12.5") val removeSourceBranchAfterMerge: Boolean,
  @SinceGitLab("13.1") val allowMergeOnSkippedPipeline: Boolean,
  @SinceGitLab("16.8") val allowsMultipleMergeRequestAssignees: Boolean?,
  @SinceGitLab("16.8") val allowsMultipleMergeRequestReviewers: Boolean?,
  val repository: Repository?
) {
  @JsonIgnore val id: GitLabGid = GitLabGidData(_id)
  val ownerPath: @NlsSafe String = fullPath.split("/").dropLast(1).joinToString("/")

  data class Repository(
    val rootRef: String?
  )
}