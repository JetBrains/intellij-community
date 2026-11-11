// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ExperimentalUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent

/**
 * Tests the form of the tool window drop placeholder. Its corners are rounded, as the corners of the editor drop placeholder are.
 * Over an editor window, only the corners at the editor area corners are rounded.
 */
@TestApplication
@RunInEdt
internal class ToolWindowDropHighlightTest {
  @Test
  fun `the corners of the drop target highlight are rounded`() {
    val image = paint(ToolWindowDragHelper.createDropTargetHighlightComponent())
    assertThat(image.alphaAt(SIZE / 2, SIZE / 2)).isGreaterThan(0)
    assertThat(image.alphaAt(0, 0)).isEqualTo(0)
    assertThat(image.alphaAt(SIZE - 1, SIZE - 1)).isEqualTo(0)
  }

  @Test
  fun `the corners of the drop hint highlight are rounded`() {
    assumeTrue(ExperimentalUI.isNewUI(), "The drop hint is painted only in the New UI")
    val image = paint(ToolWindowDragHelper.createDropHintHighlightComponent())
    assertThat(image.alphaAt(SIZE / 2, SIZE / 2)).isGreaterThan(0)
    assertThat(image.alphaAt(0, 0)).isEqualTo(0)
    assertThat(image.alphaAt(SIZE - 1, SIZE - 1)).isEqualTo(0)
  }

  @Test
  fun `the drop target highlight of the right editor window rounds only the corners at the editor area corners`() {
    val component = ToolWindowDragHelper.createDropTargetHighlightComponent()
    component.cornerArea = Rectangle(-SIZE, 0, SIZE * 2, SIZE)
    val image = paint(component)
    assertThat(image.alphaAt(SIZE / 2, SIZE / 2)).isGreaterThan(0)
    assertThat(image.alphaAt(0, 0)).isGreaterThan(0)
    assertThat(image.alphaAt(0, SIZE - 1)).isGreaterThan(0)
    assertThat(image.alphaAt(SIZE - 1, 0)).isEqualTo(0)
    assertThat(image.alphaAt(SIZE - 1, SIZE - 1)).isEqualTo(0)
  }
}

private const val SIZE = 100

private fun paint(component: JComponent): BufferedImage {
  component.setSize(SIZE, SIZE)
  val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    component.paint(g)
  }
  finally {
    g.dispose()
  }
  return image
}

private fun BufferedImage.alphaAt(x: Int, y: Int): Int = getRGB(x, y) ushr 24
