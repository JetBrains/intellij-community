// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository

internal data class GHNewPRDiffVirtualFile(private val fileManagerId: String,
                                           private val project: Project,
                                           private val repository: GHRepositoryCoordinates)
  : CodeReviewDiffVirtualFile(getFileName()) {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(fileManagerId, project, repository, null, true)
  override fun getPresentablePath() = getPresentablePath(repository)
  override fun getPresentableName() = GithubBundle.message("pull.request.new.diff.editor.title")

  override fun isValid(): Boolean = isFileValid(fileManagerId, project, repository)

  override fun createViewer(project: Project): DiffEditorViewer {
    TODO("Not implemented yet")
  }
}

private fun isFileValid(fileManagerId: String, project: Project, repository: GHRepositoryCoordinates): Boolean {
  val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository) ?: return false
  return dataContext.filesManager.id == fileManagerId
}

private fun getPresentablePath(repository: GHRepositoryCoordinates) =
  "${repository.toUrl()}/pulls/newPR.diff"

private fun getFileName(): String = "newPR.diff"
