// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.codereview.CodeReviewCombinedDiffVirtualFile
import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.CombinedDiffModel
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffService

internal data class GHPRDiffVirtualFile(private val fileManagerId: String,
                                        private val project: Project,
                                        private val repository: GHRepositoryCoordinates,
                                        private val pullRequest: GHPRIdentifier)
  : CodeReviewDiffVirtualFile(getFileName(pullRequest)) {
  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem)
    .getPath(fileManagerId, project, repository, pullRequest, true)

  override fun getPresentablePath(): String = getPresentablePath(repository, pullRequest)
  override fun getPresentableName(): String = GithubBundle.message("pull.request.diff.editor.title", pullRequest.number)

  override fun isValid(): Boolean = isFileValid(fileManagerId, project, repository)

  override fun createProcessor(project: Project): DiffRequestProcessor =
    project.service<GHPRDiffService>().createDiffRequestProcessor(repository, pullRequest)
}

internal data class GHPRCombinedDiffPreviewVirtualFile(private val fileManagerId: String,
                                                       private val project: Project,
                                                       private val repository: GHRepositoryCoordinates,
                                                       private val pullRequest: GHPRIdentifier)
  : CodeReviewCombinedDiffVirtualFile(createSourceId(fileManagerId, repository, pullRequest),
                                      getFileName(pullRequest)) {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem)
    .getPath(fileManagerId, project, repository, pullRequest, true)

  override fun getPresentablePath(): String = getPresentablePath(repository, pullRequest)
  override fun getPresentableName(): String = GithubBundle.message("pull.request.diff.editor.title", pullRequest.number)

  override fun isValid(): Boolean = isFileValid(fileManagerId, project, repository)

  override fun createModel(): CombinedDiffModel {
    return project.service<GHPRDiffService>().createCombinedDiffModel(repository, pullRequest)
  }
}

private fun createSourceId(fileManagerId: String, repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier) =
  "GitHubPullRequests:$fileManagerId:$repository/$pullRequest"

private fun isFileValid(fileManagerId: String, project: Project, repository: GHRepositoryCoordinates): Boolean {
  val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository) ?: return false
  return dataContext.filesManager.id == fileManagerId
}

private fun getPresentablePath(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier) =
  "${repository.toUrl()}/pulls/${pullRequest.number}.diff"

private fun getFileName(pullRequest: GHPRIdentifier): String = "#${pullRequest.number}.diff"