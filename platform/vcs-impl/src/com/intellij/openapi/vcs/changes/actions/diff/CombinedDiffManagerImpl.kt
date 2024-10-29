// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.tools.combined.*
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.PresentableChange

private class CombinedDiffManagerImpl(private val project: Project) : CombinedDiffManager {
  override fun createProcessor(diffPlace: String?): CombinedDiffComponentProcessor {
    val model = CombinedDiffModel(project)
    model.context.putUserData(DiffUserDataKeys.PLACE, diffPlace)
    val goToChangePopupAction = MyGoToChangePopupAction(model)
    return CombinedDiffComponentProcessorImpl(model, goToChangePopupAction)
  }
}

private class MyGoToChangePopupAction(val model: CombinedDiffModel) : PresentableGoToChangePopupAction.Default<PresentableChange>() {
 private val viewer get() = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY)

  override fun getChanges(): ListSelection<out PresentableChange> {
    val changes = model.requests.map { it.producer }.filterIsInstance<PresentableChange>()

    val selected = viewer?.getCurrentBlockId() as? CombinedPathBlockId
    val selectedIndex = when {
      selected != null -> changes.indexOfFirst {
        it.tag == selected.tag &&
        it.fileStatus == selected.fileStatus &&
        it.filePath == selected.path
      }
      else -> -1
    }
    return ListSelection.createAt(changes, selectedIndex)
  }

  override fun onSelected(change: PresentableChange) {
    viewer?.selectDiffBlock(CombinedPathBlockId(change.filePath, change.fileStatus, change.tag), true,
                            CombinedDiffViewer.ScrollPolicy.SCROLL_TO_BLOCK)
  }
}

class CombinedDiffPreviewModel {
  companion object {
    @JvmStatic
    @Deprecated("Use prepareCombinedBlocksFromWrappers", ReplaceWith("prepareCombinedBlocksFromWrappers(project, changes)"))
    fun prepareCombinedDiffModelRequests(project: Project, changes: List<Wrapper>): List<CombinedBlockProducer> {
      return prepareCombinedBlocksFromWrappers(project, changes)
    }

    @JvmStatic
    @Deprecated("Use prepareCombinedBlocksFromProducers", ReplaceWith("prepareCombinedBlocksFromProducers(changes)"))
    fun prepareCombinedDiffModelRequestsFromProducers(changes: List<ChangeDiffRequestChain.Producer>): List<CombinedBlockProducer> {
      return prepareCombinedBlocksFromProducers(changes)
    }
  }
}

internal fun prepareCombinedBlocksFromWrappers(project: Project, changes: List<Wrapper>): List<CombinedBlockProducer> {
  return changes.mapNotNull { wrapper ->
    val producer = wrapper.createProducer(project) ?: return@mapNotNull null
    val id = CombinedPathBlockId(wrapper.filePath, wrapper.fileStatus, wrapper.tag)
    CombinedBlockProducer(id, producer)
  }
}

internal fun prepareCombinedBlocksFromProducers(changes: List<ChangeDiffRequestChain.Producer>): List<CombinedBlockProducer> {
  return changes.map { producer ->
    val id = CombinedPathBlockId(producer.filePath, producer.fileStatus, null)
    CombinedBlockProducer(id, producer)
  }
}
