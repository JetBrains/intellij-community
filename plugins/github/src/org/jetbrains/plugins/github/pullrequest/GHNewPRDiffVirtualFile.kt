// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.collaboration.ui.codereview.CodeReviewAdvancedSettings
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffService
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel

internal data class GHNewPRDiffVirtualFile(private val fileManagerId: String,
                                           private val project: Project,
                                           private val repository: GHRepositoryCoordinates)
  : CodeReviewDiffVirtualFile(getFileName()) {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(fileManagerId, project, repository, null, true)
  override fun getPresentablePath() = getPresentablePath(repository)
  override fun getPresentableName() = GithubBundle.message("pull.request.new.diff.editor.title")

  override fun isValid(): Boolean = findProjectVm() != null

  override fun createViewer(project: Project): DiffEditorViewer {
    val processor = if (CodeReviewAdvancedSettings.isCombinedDiffEnabled()) {
      project.service<GHPRDiffService>().createCombinedDiffProcessor(repository)
    }
    else {
      project.service<GHPRDiffService>().createDiffRequestProcessor(repository)
    }
    processor.context.putUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE, CodeReviewAdvancedSettings.CodeReviewCombinedDiffToggle)
    return processor
  }

  private fun findProjectVm(): GHPRToolWindowProjectViewModel? =
    project.service<GHPRToolWindowViewModel>().projectVm.value?.takeIf { it.repository == repository }
}

private fun getPresentablePath(repository: GHRepositoryCoordinates) =
  "${repository.toUrl()}/pulls/newPR.diff"

private fun getFileName(): String = "newPR.diff"
