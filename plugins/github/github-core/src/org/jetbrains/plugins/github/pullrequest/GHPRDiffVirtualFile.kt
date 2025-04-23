// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffService

internal data class GHPRDiffVirtualFile(override val fileManagerId: String,
                                        private val project: Project,
                                        private val repository: GHRepositoryCoordinates,
                                        private val pullRequest: GHPRIdentifier)
  : CodeReviewDiffVirtualFile(getFileName(pullRequest)), GHPRVirtualFile {
  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem)
    .getPath(fileManagerId, project, repository, pullRequest, true)

  override fun getPresentablePath(): String = getPresentablePath(repository, pullRequest)
  override fun getPresentableName(): String = GithubBundle.message("pull.request.diff.editor.title", pullRequest.number)

  override fun isValid(): Boolean = findProjectVm() != null

  override fun createViewer(project: Project): DiffEditorViewer {
    return project.service<GHPRDiffService>().createGHPRDiffProcessor(repository, pullRequest)
  }

  private fun findProjectVm(): GHPRConnectedProjectViewModel? =
    project.service<GHPRProjectViewModel>().connectedProjectVm.value?.takeIf { it.repository == repository }

  internal class TitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? =
      when (file) {
        is GHPRDiffVirtualFile -> file.presentableName
        else -> null
      }
  }
}

private fun getPresentablePath(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier) =
  "${repository.toUrl()}/pulls/${pullRequest.number}.diff"

private fun getFileName(pullRequest: GHPRIdentifier): String = "#${pullRequest.number}.diff"
