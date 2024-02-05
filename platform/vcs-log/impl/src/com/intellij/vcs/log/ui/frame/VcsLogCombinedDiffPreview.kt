// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.diff.tools.combined.DISABLE_LOADING_BLOCKS
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.openapi.vcs.changes.actions.diff.CombinedTreeDiffPreview
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.vcs.log.VcsLogBundle

class VcsLogCombinedDiffPreview(private val browser: VcsLogChangesBrowser) : CombinedTreeDiffPreview(browser.viewer, browser) {

  override fun createPreviewModel(): CombinedDiffPreviewModel {
    val previewModel = VcsLogCombinedDiffPreviewModel(browser)
    previewModel.updateBlocks()
    return previewModel
  }

  override fun getCombinedDiffTabTitle(): String {
    val combinedDiffViewer = previewModel?.processor?.context?.getUserData(COMBINED_DIFF_VIEWER_KEY)
    val filePath = (combinedDiffViewer?.getCurrentBlockId() as? CombinedPathBlockId)?.path
                   ?: previewModel?.selected?.filePath
    return if (filePath == null) VcsLogBundle.message("vcs.log.diff.preview.editor.empty.tab.name")
    else VcsLogBundle.message("vcs.log.diff.preview.editor.tab.name", filePath.name)
  }

}

class VcsLogCombinedDiffPreviewModel(private val browser: VcsLogChangesBrowser)
  : CombinedDiffPreviewModel(browser.viewer.project, DiffPlaces.VCS_LOG_VIEW, browser) {

  init {
    processor.context.putUserData(DISABLE_LOADING_BLOCKS, true)
  }

  override fun iterateSelectedChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    return VcsLogChangeProcessor.wrap(browser, VcsTreeModelData.selected(browser.viewer))
  }

  override fun iterateAllChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    return VcsLogChangeProcessor.wrap(browser, VcsTreeModelData.all(browser.viewer))
  }

  override fun selectChangeInSourceComponent(change: ChangeViewDiffRequestProcessor.Wrapper) {
    ChangesBrowserBase.selectObjectWithTag(browser.viewer, change.userObject, change.tag)
  }
}
