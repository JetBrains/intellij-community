// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

class GitLabMergeRequestApprovalRestDTO(
  override val iid: String,
  approvedBy: List<ApprovedBy>
) : GitLabMergeRequestId {

  val approvedBy: List<GitLabUserDTO> = approvedBy.map { GitLabUserDTO.fromRestDTO(it.user) }

  class ApprovedBy(val user: GitLabUserRestDTO)
}