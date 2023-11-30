// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  fun updatePreview(fromModelRefresh: Boolean) {
    if (isPreviewVisible) {
      updatePreviewProcessor.refresh(fromModelRefresh)
    }
    else {
      updatePreviewProcessor.clear()
    }
  }

  override fun openPreview(requestFocus: Boolean): Boolean {
    setPreviewVisible(true)
    updatePreview(false)
    return true
  }

  override fun closePreview() {
    setPreviewVisible(false)
    updatePreview(false)
  }

  private fun setPreviewVisible(isVisible: Boolean) {
    if (isPreviewVisible == isVisible) return
    isPreviewVisible = isVisible

    if (isVisible) {
      secondComponent = updatePreviewProcessor.component.also {
        updateComponentTreeUI(it)
        it.minimumSize = emptySize()
      }
    }
    else {
      secondComponent = null
    }

    validate()
    repaint()
  }
}
