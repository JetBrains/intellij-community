// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.tools.combined.*
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.toListIfNotMany
import com.intellij.openapi.vcs.changes.ui.PresentableChange

class CombinedChangeDiffComponentFactoryProvider : CombinedDiffComponentFactoryProvider {
  override fun create(model: CombinedDiffModel): CombinedDiffComponentFactory = MyFactory(model)

  private inner class MyFactory(model: CombinedDiffModel) : CombinedDiffComponentFactory(model) {

    init {
      model.cleanBlocks()
    }

    override fun createGoToChangeAction(): AnAction = MyGoToChangePopupAction()
    private inner class MyGoToChangePopupAction : PresentableGoToChangePopupAction.Default<PresentableChange>() {

      val viewer get() = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY)

      override fun getChanges(): ListSelection<out PresentableChange> {
        val changes =
          if (model is CombinedDiffPreviewModel) model.iterateAllChanges().toList()
          else model.requests.values.filterIsInstance<PresentableChange>()

        val selected = viewer?.getCurrentBlockId() as? CombinedPathBlockId
        val selectedIndex = when {
          selected != null -> changes.indexOfFirst { it.tag == selected.tag
                                                     && it.fileStatus == selected.fileStatus
                                                     && it.filePath == selected.path }
          else -> -1
        }
        return ListSelection.createAt(changes, selectedIndex)
      }

      override fun canNavigate(): Boolean {
        if (model is CombinedDiffPreviewModel) {
          val allChanges = toListIfNotMany(model.iterateAllChanges(), true)
          return allChanges == null || allChanges.size > 1
        }

        return super.canNavigate()
      }

      override fun onSelected(change: PresentableChange) {
        if (model is CombinedDiffPreviewModel && change is Wrapper) {
          model.selected = change
        }
        else {
          viewer?.selectDiffBlock(CombinedPathBlockId(change.filePath, change.fileStatus, change.tag), true,
                                  CombinedDiffViewer.ScrollPolicy.SCROLL_TO_BLOCK)
        }
      }
    }
  }

}
