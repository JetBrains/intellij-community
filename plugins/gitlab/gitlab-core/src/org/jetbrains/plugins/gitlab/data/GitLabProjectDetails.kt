// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.data

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabPlan
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectSquashOptionRest
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath

data class GitLabProjectDetails(
  val id: String,
  val path: GitLabProjectPath,
  val name: @Nls String,
  val nameWithNamespace: @Nls String,
  val httpUrlToRepo: @NlsSafe String?,
  val sshUrlToRepo: @NlsSafe String?,
  val defaultBranch: String?,
  val squashBeforeMergeReadOnly: Boolean,
  // can be null when loaded with GQL
  val squashBeforeMergeDefault: Boolean?,
  val removeSourceBranchAfterMerge: Boolean?,
  // can be null when loaded with REST and namespace plan loading failed
  val allowsMultipleMergeRequestAssignees: Boolean?,
  val allowsMultipleMergeRequestReviewers: Boolean?,
) {
  constructor(path: GitLabProjectPath, dto: GitLabProjectDTO) : this(
    id = dto.id.guessRestId(),
    path = path,
    name = dto.name,
    nameWithNamespace = dto.nameWithNamespace,
    httpUrlToRepo = dto.httpUrlToRepo,
    sshUrlToRepo = dto.sshUrlToRepo,
    defaultBranch = dto.repository?.rootRef,
    squashBeforeMergeReadOnly = dto.squashReadOnly,
    squashBeforeMergeDefault = null,
    removeSourceBranchAfterMerge = dto.removeSourceBranchAfterMerge,
    allowsMultipleMergeRequestAssignees = dto.allowsMultipleMergeRequestAssignees,
    allowsMultipleMergeRequestReviewers = dto.allowsMultipleMergeRequestReviewers
  )

  constructor(parsedPath: GitLabProjectPath, dto: GitLabProjectRestDTO, plan: GitLabPlan?) : this(
    id = dto.id,
    name = dto.name,
    nameWithNamespace = dto.nameWithNamespace,
    path = parsedPath,
    httpUrlToRepo = dto.httpUrlToRepo,
    sshUrlToRepo = dto.sshUrlToRepo,
    defaultBranch = dto.defaultBranch,
    squashBeforeMergeReadOnly = dto.squashOption == GitLabProjectSquashOptionRest.always
                                || dto.squashOption == GitLabProjectSquashOptionRest.never,
    squashBeforeMergeDefault = when (dto.squashOption) {
      GitLabProjectSquashOptionRest.never, GitLabProjectSquashOptionRest.default_off -> false
      GitLabProjectSquashOptionRest.always, GitLabProjectSquashOptionRest.default_on -> true
    },
    removeSourceBranchAfterMerge = dto.removeSourceBranchAfterMerge,
    allowsMultipleMergeRequestAssignees = plan?.let { it != GitLabPlan.FREE },
    allowsMultipleMergeRequestReviewers = plan?.let { it != GitLabPlan.FREE }
  )
}