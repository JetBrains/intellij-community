// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import kotlinx.coroutines.CancellationException
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffService

internal data class GHNewPRDiffVirtualFile(
  override val fileManagerId: String,
  private val project: Project,
  private val repository: GHRepositoryCoordinates,
)
  : CodeReviewDiffVirtualFile(getFileName()), GHPRVirtualFile {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(fileManagerId, project, repository, null, true)
  override fun getPresentablePath() = getPresentablePath(repository)
  override fun getPresentableName() = GithubBundle.message("pull.request.new.diff.editor.title")

  override fun isValid(): Boolean = findProjectVm() != null

  override fun createViewer(project: Project): DiffEditorViewer {
    return project.service<GHPRDiffService>().createGHNewPRDiffProcessor(repository)
  }

  private fun findProjectVm(): GHPRConnectedProjectViewModel? {
    try {
      if (project.isDisposed) return null
      return project.service<GHPRProjectViewModel>().connectedProjectVm.value?.takeIf { it.repository == repository }
    }
    catch (e: CancellationException) {
      logger<GHNewPRDiffVirtualFile>().error(RuntimeException(e))
      return null
    }
    catch (e: Exception) {
      logger<GHNewPRDiffVirtualFile>().error(e)
      return null
    }
  }
}

private fun getPresentablePath(repository: GHRepositoryCoordinates) =
  "${repository.toUrl()}/pulls/newPR.diff"

private fun getFileName(): String = "newPR.diff"
