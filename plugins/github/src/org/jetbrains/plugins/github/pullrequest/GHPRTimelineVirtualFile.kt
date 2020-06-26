// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.Icon
import kotlin.properties.Delegates.observable

internal class GHPRTimelineVirtualFile(fileManagerId: String,
                                       project: Project,
                                       repository: GHRepositoryCoordinates,
                                       pullRequest: GHPRIdentifier)
  : GHPRVirtualFile(fileManagerId, project, repository, pullRequest) {

  var details: GHPullRequestShort? by observable(pullRequest as? GHPullRequestShort) { _, _, _ ->
    modificationStamp = modificationStamp++
  }

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun getName() = "#${pullRequest.number}"
  override fun getPresentableName() = details?.let { "${it.title} $name" } ?: name

  override fun getPath(): String = GHPRVirtualFileSystem.getPath(fileManagerId, project, repository, pullRequest)
  override fun getPresentablePath() = details?.url ?: "${repository.toUrl()}/pulls/${pullRequest.number}"

  fun getIcon(): Icon? = details?.let { GithubUIUtil.getPullRequestStateIcon(it.state, it.isDraft) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRTimelineVirtualFile) return false
    if (!super.equals(other)) return false
    return true
  }
}