// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.ComplexPathVirtualFileWithoutContent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
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

  override fun getName() = GHPRTimelineUIUtil.getName(pullRequest)
  override fun getPresentableName() = GHPRTimelineUIUtil.getPresentableName(project, repository, pullRequest)

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(sessionId, project, repository, pullRequest, false)
  override fun getPresentablePath() = GHPRTimelineUIUtil.getPresentablePath(project, repository, pullRequest)

  fun getIcon(): Icon? = GHPRTimelineUIUtil.getIcon(project, repository, pullRequest)

  override fun isValid(): Boolean = findProjectVm() != null

  override fun setValid(valid: Boolean) = Unit

  fun findProjectVm(): GHPRConnectedProjectViewModel? =
    project.service<GHPRProjectViewModel>().connectedProjectVm.value?.takeIf { it.repository == repository }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRTimelineVirtualFile) return false
    if (!super.equals(other)) return false

    if (project != other.project) return false
    if (repository != other.repository) return false
    return pullRequest == other.pullRequest
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + repository.hashCode()
    result = 31 * result + pullRequest.hashCode()
    return result
  }
}
