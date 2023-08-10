// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.ProjectMemberDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount

internal sealed interface GitLabCloneListItem {
  val account: GitLabAccount

  data class Repository(
    override val account: GitLabAccount,
    val projectMember: ProjectMemberDTO
  ) : GitLabCloneListItem

  data class Error(
    override val account: GitLabAccount,
    val message: @Nls String
  ) : GitLabCloneListItem
}

internal fun GitLabCloneListItem.Repository.presentation(): @NlsSafe String = projectMember.project.nameWithNamespace