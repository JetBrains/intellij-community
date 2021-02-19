// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.editor.DiffContentVirtualFile
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

@Suppress("EqualsOrHashCode")
internal class GHNewPRDiffVirtualFile(fileManagerId: String,
                                      project: Project,
                                      repository: GHRepositoryCoordinates)
  : GHRepoVirtualFile(fileManagerId, project, repository), DiffContentVirtualFile {

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun getName() = "newPR.diff"
  override fun getPresentableName() = GithubBundle.message("pull.request.new.diff.editor.title")

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(fileManagerId, project, repository, null, true)
  override fun getPresentablePath() = "${repository.toUrl()}/pulls/newPR.diff"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHNewPRDiffVirtualFile) return false
    if (!super.equals(other)) return false
    return true
  }
}