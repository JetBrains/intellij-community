// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.tools.combined.DISABLE_LOADING_BLOCKS
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.openapi.vcs.changes.actions.diff.CombinedTreeDiffPreview
import com.intellij.openapi.vcs.changes.actions.diff.CombinedTreeDiffPreviewModel
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.vcs.log.VcsLogBundle

class VcsLogCombinedDiffPreview(private val browser: VcsLogChangesBrowser) : CombinedTreeDiffPreview(browser.viewer, browser) {

  override fun createPreviewModel(): CombinedDiffPreviewModel {
    val previewModel = VcsLogCombinedDiffPreviewModel(browser)
    val blocks = CombinedDiffPreviewModel.prepareCombinedDiffModelRequests(browser.viewer.project,
                                                                           previewModel.iterateAllChanges().toList())
    previewModel.model.setBlocks(blocks)
    return previewModel
  }

  override fun getCombinedDiffTabTitle(): String {
    val filePath = previewModel?.selected?.filePath
    return if (filePath == null) VcsLogBundle.message("vcs.log.diff.preview.editor.empty.tab.name")
    else VcsLogBundle.message("vcs.log.diff.preview.editor.tab.name", filePath.name)
  }

}

class VcsLogCombinedDiffPreviewModel(private val browser: VcsLogChangesBrowser) : CombinedTreeDiffPreviewModel(browser.viewer, browser) {

  init {
    model.context.putUserData(DISABLE_LOADING_BLOCKS, true)
    model.context.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.VCS_LOG_VIEW)
  }

  override fun iterateSelectedChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    return VcsLogChangeProcessor.wrap(browser, VcsTreeModelData.selected(tree))
  }

  override fun iterateAllChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    return VcsLogChangeProcessor.wrap(browser, VcsTreeModelData.all(tree))
  }
}
