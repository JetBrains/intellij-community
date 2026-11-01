// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus

@SinceGitLab("13.3.1")
@GraphQLFragment("/graphql/fragment/ciJob.graphql")
data class GitLabCiJobDTO(
  val name: String,
  @SinceGitLab("13.11") val status: GitLabCiJobStatus?,
  @SinceGitLab("13.11") val allowFailure: Boolean?,
  @SinceGitLab("13.5") val detailedStatus: DetailedStatus?
) {
  data class DetailedStatus(val detailsPath: String?)
}