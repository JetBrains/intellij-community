// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent

@TestApplication
internal class AppearanceConfigurableTest {
  @Test
  fun `the tool window layout preview keeps its size and position on a wide page`(): Unit = timeoutRunBlocking {
    val preview = layOutPreview { page -> page.preferredSize.width + 1000 }

    assertThat(preview.bounds).isEqualTo(preview.boundsAtPreferredWidth)
    assertThat(preview.bounds.size).isEqualTo(preview.preferredSize)
  }

  @Test
  fun `the tool window layout preview keeps its size on a page of the minimum width`(): Unit = timeoutRunBlocking {
    val preview = layOutPreview { page -> page.minimumSize.width }

    assertThat(preview.bounds.size).isEqualTo(preview.preferredSize)
  }
}

private class PreviewLayout(val preferredSize: Dimension, val boundsAtPreferredWidth: Rectangle, val bounds: Rectangle)

/**
 * Builds the Appearance page and lays it out at its preferred width, and then at the width that [pageWidth] gives.
 * Returns the bounds of the tool window layout preview after each layout.
 */
private suspend fun layOutPreview(pageWidth: (JComponent) -> Int): PreviewLayout = withContext(Dispatchers.EDT) {
  val configurable = AppearanceConfigurable()
  try {
    val page = configurable.createComponent()!!
    val preview = UIUtil.findComponentOfType(page, ToolWindowLayoutPreviewPanel::class.java)!!
    val boundsAtPreferredWidth = layOut(page, page.preferredSize.width, preview)
    PreviewLayout(preview.preferredSize, boundsAtPreferredWidth, layOut(page, pageWidth(page), preview))
  }
  finally {
    configurable.disposeUIResources()
  }
}

private fun layOut(page: JComponent, width: Int, preview: JComponent): Rectangle {
  page.setSize(width, page.preferredSize.height)
  page.doLayout()
  return preview.bounds
}
