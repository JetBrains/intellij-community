// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.Icon

@Suppress("EqualsOrHashCode")
internal class GHPRTimelineVirtualFile(fileManagerId: String,
                                       project: Project,
                                       repository: GHRepositoryCoordinates,
                                       pullRequest: GHPRIdentifier)
  : GHPRVirtualFile(fileManagerId, project, repository, pullRequest) {

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun getName() = "#${pullRequest.number}"
  override fun getPresentableName() = findDetails()?.let { "${it.title} $name" } ?: name

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(sessionId, project, repository, pullRequest, false)
  override fun getPresentablePath() = findDetails()?.url ?: "${repository.toUrl()}/pulls/${pullRequest.number}"

  fun getIcon(): Icon? = findDetails()?.let { GHUIUtil.getPullRequestStateIcon(it.state, it.isDraft) }

  private fun findDetails(): GHPullRequestShort? =
    project.service<GHRepositoryConnectionManager>().connectionState.value?.dataContext?.run {
      listLoader.loadedData.find { it.id == pullRequest.id }
      ?: dataProviderRepository.findDataProvider(pullRequest)?.detailsData?.loadedDetails
    }


  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRTimelineVirtualFile) return false
    return super.equals(other)
  }
}
