// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.codereview.CodeReviewCombinedDiffVirtualFile
import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.CombinedBlockProducer
import com.intellij.diff.tools.combined.CombinedDiffModel
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.MutableDiffRequestChainProcessor
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

  override fun createProcessor(project: Project): DiffRequestProcessor {
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!
    val diffRequestModel = dataContext.newPRDiffModel

    return MutableDiffRequestChainProcessor(project, null).also {
      diffRequestModel.process(it)
    }
  }
}

internal data class GHNewPRCombinedDiffPreviewVirtualFile(private val fileManagerId: String,
                                                          private val project: Project,
                                                          private val repository: GHRepositoryCoordinates)
  : CodeReviewCombinedDiffVirtualFile("GitHubNewPullRequests:$fileManagerId:$repository", getFileName()) {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GHPRVirtualFileSystem.getInstance()

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(fileManagerId, project, repository, null, true)
  override fun getPresentablePath() = getPresentablePath(repository)
  override fun getPresentableName() = GithubBundle.message("pull.request.new.diff.editor.title")

  override fun isValid(): Boolean = isFileValid(fileManagerId, project, repository)

  override fun createModel(): CombinedDiffModel {
    val model = CombinedDiffModelImpl(project)
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!
    val diffModel: GHPRDiffRequestModel = dataContext.newPRDiffModel
    diffModel.addAndInvokeRequestChainListener(model.ourDisposable) {
      model.cleanBlocks()
      diffModel.requestChain?.let<DiffRequestChain, Unit> {
        val requests = mutableListOf<CombinedBlockProducer>()
        for (request in it.requests) {
          if (request !is ChangeDiffRequestProducer) return@addAndInvokeRequestChainListener
          val change = request.change
          val blockId = CombinedPathBlockId((change.afterRevision?.file ?: change.beforeRevision?.file)!!, change.fileStatus, null)
          requests += CombinedBlockProducer(blockId, request)
        }
        model.setBlocks(requests)
      }
    }
    return model
  }

}

private fun isFileValid(fileManagerId: String, project: Project, repository: GHRepositoryCoordinates): Boolean {
  val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository) ?: return false
  return dataContext.filesManager.id == fileManagerId
}

private fun getPresentablePath(repository: GHRepositoryCoordinates) =
  "${repository.toUrl()}/pulls/newPR.diff"

private fun getFileName(): String = "newPR.diff"