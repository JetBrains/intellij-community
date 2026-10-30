// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI.emptySize
import javax.swing.SwingUtilities.updateComponentTreeUI

class PreviewDiffSplitterComponent(
  private val updatePreviewProcessor: DiffPreviewUpdateProcessor,
  proportionKey: String,
) : OnePixelSplitter(proportionKey, 0.3f), DiffPreview {

  private var isPreviewVisible = false

  override fun openPreview(requestFocus: Boolean): Boolean {
    setPreviewVisible(true)
    // diff may need the platform locks, and we can't guarantee the correct dispatcher, so let's update with a delay
    ApplicationManager.getApplication()
      .invokeLater({
                     if (isPreviewVisible) {
                       updatePreviewProcessor.refresh(false)
                     }
                   }, ModalityState.current())
    return true
  }

  override fun closePreview() {
    setPreviewVisible(false)
    updatePreviewProcessor.clear()
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