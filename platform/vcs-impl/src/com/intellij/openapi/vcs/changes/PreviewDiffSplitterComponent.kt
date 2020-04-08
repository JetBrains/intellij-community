// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.ui.OnePixelSplitter
import com.intellij.util.IJSwingUtilities.updateComponentTreeUI
import com.intellij.util.ui.JBUI.emptySize

class PreviewDiffSplitterComponent(
  private val updatePreviewProcessor: DiffPreviewUpdateProcessor,
  proportionKey: String
) : OnePixelSplitter(proportionKey, 0.3f),
    DiffPreview {

  private var isPreviewVisible = false

  override fun updatePreview(fromModelRefresh: Boolean) =
    updatePreviewProcessor.run { if (isPreviewVisible) refresh(fromModelRefresh) else clear() }

  override fun setPreviewVisible(isPreviewVisible: Boolean) {
    this.isPreviewVisible = isPreviewVisible
    if (this.isPreviewVisible == (secondComponent == null)) {
      updateVisibility()
    }
    updatePreview(false)
  }

  private fun updateVisibility() {
    secondComponent = if (isPreviewVisible) updatePreviewProcessor.component else null
    secondComponent?.let {
      updateComponentTreeUI(it)
      it.minimumSize = emptySize()
    }

    validate()
    repaint()
  }
}