// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.dto.ProjectMemberDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount

internal data class GitLabCloneListItem(
  val account: GitLabAccount,
  val projectMember: ProjectMemberDTO
)

internal fun GitLabCloneListItem.presentation(): @NlsSafe String {
  return "${projectMember.createdBy.username}/${projectMember.project.name}"
}