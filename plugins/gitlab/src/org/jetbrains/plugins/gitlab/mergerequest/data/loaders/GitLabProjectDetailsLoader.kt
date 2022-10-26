// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.request.getAllProjectMembers

internal class GitLabProjectDetailsLoader(
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
) {
  suspend fun projectMembers(): List<GitLabMemberDTO> = api.getAllProjectMembers(project)
}