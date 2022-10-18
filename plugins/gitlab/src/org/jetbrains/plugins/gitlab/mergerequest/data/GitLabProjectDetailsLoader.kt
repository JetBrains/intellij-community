// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.request.getProjectMembers

internal class GitLabProjectDetailsLoader(
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
) {
  suspend fun projectMembers(): List<GitLabMemberDTO> = api.getProjectMembers(project)
}