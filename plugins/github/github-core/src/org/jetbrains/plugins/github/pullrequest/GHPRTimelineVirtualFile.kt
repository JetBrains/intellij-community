// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.ComplexPathVirtualFileWithoutContent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.Icon

internal data class GHPRTimelineVirtualFile(
  override val fileManagerId: String,
  val project: Project,
  val repository: GHRepositoryCoordinates,
  val pullRequest: GHPRIdentifier,
) : ComplexPathVirtualFileWithoutContent(fileManagerId), GHPRVirtualFile {

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getName() = "#${pullRequest.number}"
  override fun getPresentableName() = findDetails()?.let { "${it.title} $name" } ?: name

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(sessionId, project, repository, pullRequest, false)
  override fun getPresentablePath() = findDetails()?.url ?: "${repository.toUrl()}/pulls/${pullRequest.number}"

  fun getIcon(): Icon? = findDetails()?.let { GHUIUtil.getPullRequestStateIcon(it.state, it.isDraft) }

  override fun isValid(): Boolean = findProjectVm() != null

  override fun setValid(valid: Boolean) = Unit

  fun findProjectVm(): GHPRConnectedProjectViewModel? =
    project.service<GHPRToolWindowViewModel>().projectVm.value?.takeIf { it.repository == repository }

  private fun findDetails(): GHPullRequestShort? = findProjectVm()?.findDetails(pullRequest)
}
