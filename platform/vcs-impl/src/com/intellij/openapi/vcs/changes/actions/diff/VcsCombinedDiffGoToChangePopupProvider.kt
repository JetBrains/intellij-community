// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedBlockProducer
import com.intellij.diff.tools.combined.CombinedDiffGoToChangePopupProvider
import com.intellij.diff.tools.combined.CombinedDiffModel
import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import org.jetbrains.annotations.ApiStatus


/** Shows the changes tree popup of [GoToChangePopupUtil] for the [PresentableChange] blocks of a combined diff. */
internal class VcsCombinedDiffGoToChangePopupProvider : CombinedDiffGoToChangePopupProvider {
  override fun createGoToChangeAction(model: CombinedDiffModel): AnAction {
    val controller = MyGoToChangePopupController(model)
    return PresentableGoToChangePopupAction.create({ controller.getChanges() }, controller)
  }
}

private class MyGoToChangePopupController(private val model: CombinedDiffModel) : GoToChangePopupController<PresentableChange> {
  private val viewer get() = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY)

  fun getChanges(): ListSelection<out PresentableChange> {
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

  override fun getPresentation(change: PresentableChange): PresentableChange = change

  override fun onSelected(change: PresentableChange) {
    viewer?.selectDiffBlock(CombinedPathBlockId(change.filePath, change.fileStatus, change.tag), true,
                            CombinedDiffViewer.ScrollPolicy.SCROLL_TO_BLOCK)
  }
}

internal fun prepareCombinedBlocksFromWrappers(project: Project, changes: List<Wrapper>): List<WrapperCombinedBlockProducer> {
  return changes.mapNotNull { wrapper ->
    val producer = wrapper.createProducer(project) ?: return@mapNotNull null
    WrapperCombinedBlockProducer(wrapper, producer)
  }
}

internal fun prepareCombinedBlocksFromProducers(changes: List<ChangeDiffRequestChain.Producer>): List<CombinedBlockProducer> {
  return changes.map { producer ->
    val id = CombinedPathBlockId(producer.filePath, producer.fileStatus, null)
    CombinedBlockProducer(id, producer)
  }
}

@ApiStatus.Experimental
internal class WrapperCombinedBlockProducer(
  val wrapper: Wrapper,
  producer: DiffRequestProducer,
) : CombinedBlockProducer(
  id = CombinedPathBlockId(wrapper.filePath, wrapper.fileStatus, wrapper.tag),
  producer) {
}
