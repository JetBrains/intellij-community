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
    override fun createGoToChangeAction(): AnAction = MyGoToChangePopupAction(model)
  }
}

private class MyGoToChangePopupAction(val model: CombinedDiffModel) : PresentableGoToChangePopupAction.Default<PresentableChange>() {

  val viewer get() = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY)
  val previewModel get() = model.context.getUserData(COMBINED_DIFF_PREVIEW_MODEL)

  override fun getChanges(): ListSelection<out PresentableChange> {
    val previewModel = previewModel
    val changes = if (previewModel != null) {
      previewModel.iterateAllChanges().toList()
    }
    else {
      model.requests.map { it.producer }.filterIsInstance<PresentableChange>()
    }

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

  override fun canNavigate(): Boolean {
    val previewModel = previewModel
    if (previewModel != null) {
      val allChanges = toListIfNotMany(previewModel.iterateAllChanges(), true)
      return allChanges == null || allChanges.size > 1
    }

    return super.canNavigate()
  }

  override fun onSelected(change: PresentableChange) {
    val previewModel = previewModel
    if (previewModel != null && change is Wrapper) {
      previewModel.selected = change
    }
    else {
      viewer?.selectDiffBlock(CombinedPathBlockId(change.filePath, change.fileStatus, change.tag), true,
                              CombinedDiffViewer.ScrollPolicy.SCROLL_TO_BLOCK)
    }
  }
}
