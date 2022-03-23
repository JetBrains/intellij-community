// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.*
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesComparator
import org.jetbrains.annotations.Nls

class CombinedChangeDiffVirtualFile(requestProducer: CombinedChangeDiffRequestProducer) :
  CombinedDiffVirtualFile<CombinedChangeDiffRequestProducer>(requestProducer) {

  override fun createProcessor(project: Project) = CombinedChangeDiffRequestProcessor(project, requestProducer)
}

class CombinedChangeDiffRequestProducer(internal val producers: List<ChangeDiffRequestChain.Producer>,
                                        private val title: @Nls String? = null) : CombinedDiffRequestProducer {
  override fun getFilesSize(): Int = producers.size

  override fun getName(): String = title ?: VcsBundle.message("changes.combined.diff")

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val filePathComparator = ChangesComparator.getFilePathComparator(true)
    val requests = producers
      .asSequence()
      .map(::createChildRequest)
      .sortedWith { r1, r2 ->
        val id1 = r1.blockId
        val id2 = r2.blockId
        when {
          id1 is CombinedPathBlockId && id2 is CombinedPathBlockId -> filePathComparator.compare(id1.path, id2.path)
          else -> -1
        }
       }
      .toList()

    return CombinedDiffRequest(name, requests)
  }

  private fun createChildRequest(producer: ChangeDiffRequestChain.Producer): CombinedDiffRequest.ChildDiffRequest {
    val blockId = CombinedPathBlockId(producer.filePath, producer.fileStatus)
    return CombinedDiffRequest.ChildDiffRequest(producer, blockId)
  }
}

class CombinedChangeDiffRequestProcessor(project: Project?,
                                         private val requestProducer: CombinedChangeDiffRequestProducer) :
  CombinedDiffRequestProcessor(project, requestProducer) {

  override fun createGoToChangeAction(): AnAction = MyGoToChangePopupAction()

  private inner class MyGoToChangePopupAction : PresentableGoToChangePopupAction.Default<ChangeDiffRequestChain.Producer>() {
    override fun getChanges(): ListSelection<out ChangeDiffRequestChain.Producer> {
      val changes = requestProducer.producers
      val selected = viewer?.getCurrentBlockId() as? CombinedPathBlockId
      val selectedIndex = when {
        selected != null -> changes.indexOfFirst { it.fileStatus == selected.fileStatus && it.filePath == selected.path }
        else -> -1
      }
      return ListSelection.createAt(changes, selectedIndex)
    }

    override fun onSelected(change: ChangeDiffRequestChain.Producer) {
      viewer?.selectDiffBlock(change.filePath, change.fileStatus, ScrollPolicy.DIFF_BLOCK)
    }
  }
}
