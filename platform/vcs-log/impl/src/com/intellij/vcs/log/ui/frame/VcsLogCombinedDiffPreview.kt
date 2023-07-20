// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreview
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.vcs.log.VcsLogBundle

class VcsLogCombinedDiffPreview(private val browser: VcsLogChangesBrowser) : CombinedDiffPreview(browser.viewer, browser) {

  override fun createModel(): CombinedDiffPreviewModel = VcsLogCombinedDiffPreviewModel(browser)

  override fun getCombinedDiffTabTitle(): String {
    val filePath = model.selected?.filePath
    return if (filePath == null) VcsLogBundle.message("vcs.log.diff.preview.editor.empty.tab.name")
    else VcsLogBundle.message("vcs.log.diff.preview.editor.tab.name", filePath.name)
  }

}

class VcsLogCombinedDiffPreviewModel(private val browser: VcsLogChangesBrowser) :
  CombinedDiffPreviewModel(browser.viewer, browser) {

  override fun iterateSelectedChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
   return VcsLogChangeProcessor.wrap(browser, VcsTreeModelData.selected(tree))
  }

  override fun iterateAllChanges(): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    return VcsLogChangeProcessor.wrap(browser, VcsTreeModelData.all(tree))
  }
}
