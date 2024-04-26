// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.file.codereview.CodeReviewDiffVirtualFile
import com.intellij.collaboration.ui.codereview.CodeReviewAdvancedSettings
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.combined.CombinedBlockProducer
import com.intellij.diff.tools.combined.CombinedDiffManager
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.diff.util.DiffUserDataKeysEx
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

  override fun createViewer(project: Project): DiffEditorViewer {
    if (CodeReviewAdvancedSettings.isCombinedDiffEnabled()) {
      val processor = CombinedDiffManager.getInstance(project).createProcessor()
      val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!
      val diffModel: GHPRDiffRequestModel = dataContext.newPRDiffModel
      diffModel.addAndInvokeRequestChainListener(processor.disposable) {
        processor.cleanBlocks()
        diffModel.requestChain?.let<DiffRequestChain, Unit> {
          val requests = mutableListOf<CombinedBlockProducer>()
          for (request in it.requests) {
            if (request !is ChangeDiffRequestProducer) return@addAndInvokeRequestChainListener
            val change = request.change
            val blockId = CombinedPathBlockId((change.afterRevision?.file ?: change.beforeRevision?.file)!!, change.fileStatus, null)
            requests += CombinedBlockProducer(blockId, request)
          }
          processor.setBlocks(requests)
        }
      }
      processor.context.putUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE, CodeReviewAdvancedSettings.CodeReviewCombinedDiffToggle)
      return processor
    }
    else {
      val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!
      val diffRequestModel = dataContext.newPRDiffModel

      return MutableDiffRequestChainProcessor(project, null).also {
        it.context.putUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE, CodeReviewAdvancedSettings.CodeReviewCombinedDiffToggle)
        diffRequestModel.process(it)
      }
    }
  }
}

private fun isFileValid(fileManagerId: String, project: Project, repository: GHRepositoryCoordinates): Boolean {
  val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository) ?: return false
  return dataContext.filesManager.id == fileManagerId
}

private fun getPresentablePath(repository: GHRepositoryCoordinates) =
  "${repository.toUrl()}/pulls/newPR.diff"

private fun getFileName(): String = "newPR.diff"
