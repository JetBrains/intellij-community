// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data.loaders

import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.request.getAllProjectMembers
import org.jetbrains.plugins.gitlab.api.request.loadAllProjectLabels

internal class GitLabProjectDetailsLoader(connection: GitLabProjectConnection) {
  private val api = connection.apiClient
  private val project = connection.repo.repository

  suspend fun projectMembers(): List<GitLabMemberDTO> = api.getAllProjectMembers(project)

  suspend fun projectLabels(): List<GitLabLabelDTO> = api.loadAllProjectLabels(project)
}