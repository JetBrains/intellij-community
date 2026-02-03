// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.Icon

@ApiStatus.Internal
object GHPRTimelineUIUtil {
  fun getName(pullRequest: GHPRIdentifier): @NlsSafe String = "#${pullRequest.number}"
  fun getPresentableName(project: Project, repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): @NlsSafe String =
    findDetails(project, repository, pullRequest)?.let { "${it.title} ${getName(pullRequest)}" } ?: getName(pullRequest)

  fun getPresentablePath(project: Project, repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): @NlsSafe String =
    findDetails(project, repository, pullRequest)?.url ?: "${repository.toUrl()}/pulls/${pullRequest.number}"

  fun getIcon(project: Project, repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): Icon? =
    findDetails(project, repository, pullRequest)?.let { GHUIUtil.getPullRequestStateIcon(it.state, it.isDraft) }

  private fun findProjectVm(project: Project, repository: GHRepositoryCoordinates): GHPRConnectedProjectViewModel? =
    project.service<GHPRProjectViewModel>().connectedProjectVm.value?.takeIf { it.repository == repository }

  private fun findDetails(project: Project, repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): GHPullRequestShort? =
    findProjectVm(project, repository)?.findDetails(pullRequest)
}