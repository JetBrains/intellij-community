// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

internal class GHPRDiffVirtualFile(fileManagerId: String,
                                   project: Project,
                                   repository: GHRepositoryCoordinates,
                                   pullRequest: GHPRIdentifier)
  : GHPRVirtualFile(fileManagerId, project, repository, pullRequest) {

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun getName() = "#${pullRequest.number}.diff"
  override fun getPresentableName() = GithubBundle.message("pull.request.diff.editor.title", pullRequest.number)

  override fun getPath(): String = GHPRVirtualFileSystem.getPath(fileManagerId, project, repository, pullRequest, true)
  override fun getPresentablePath() = "${repository.toUrl()}/pulls/${pullRequest.number}.diff"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRDiffVirtualFile) return false
    if (!super.equals(other)) return false
    return true
  }
}