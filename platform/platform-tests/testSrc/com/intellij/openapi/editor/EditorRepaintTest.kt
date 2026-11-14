// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.RepaintManager

@TestApplication
class EditorRepaintTest : EditorPaintingTestCase() {
  @Test
  fun testBlockInlayRepaintStartsOneLineAboveWhileRounding(): Unit = myFixture.runTest {
    val state = setSelectionLook(true, true)
    try {
      initText("abcd\nefgh\nijkl\nmnop\nqrst\nuvwx")
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, 1000, 1000)
      val regions = recordRepaints(editor.contentComponent) {
        addBlockInlay(editor.document.getLineStartOffset(4), true)
      }
      val lineAboveTop = editor.visualLineToY(3)
      assertTrue(regions.any { it.y == lineAboveTop }, "no repaint starts at the line above the inlay: $regions")
    }
    finally {
      restoreSelectionState(state)
    }
  }

  @Test
  fun testHorizontalScrollRepaintsVisibleAreaWhileRounding(): Unit = myFixture.runTest {
    val state = setSelectionLook(true, true)
    try {
      initText("abcdefghijklmnopqrstuvwxyz\nabcdefghijklmnopqrstuvwxyz")
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, 50, 1000)
      val viewport = (editor as EditorEx).scrollPane.viewport
      viewport.viewPosition = Point(10, 0)
      val regions = recordRepaints(editor.contentComponent) {
        viewport.viewPosition = Point(20, 0)
      }
      val visibleArea = editor.scrollingModel.visibleArea
      assertEquals(20, visibleArea.x, "the visible area did not scroll")
      assertTrue(regions.any { it.contains(visibleArea) }, "no repaint holds the visible area $visibleArea: $regions")
    }
    finally {
      restoreSelectionState(state)
    }
  }
}

/**
 * Runs [action] and gives the regions that it marks dirty in [component].
 * Nothing paints these regions.
 */
private fun recordRepaints(component: JComponent, action: () -> Unit): List<Rectangle> {
  val oldManager = RepaintManager.currentManager(component)
  val recorder = RecordingRepaintManager(component)
  RepaintManager.setCurrentManager(recorder)
  try {
    action()
  }
  finally {
    RepaintManager.setCurrentManager(oldManager)
  }
  return recorder.regions
}

/**
 * Keeps each dirty region of [component], and drops every other request.
 */
private class RecordingRepaintManager(private val component: JComponent) : RepaintManager() {
  val regions: MutableList<Rectangle> = ArrayList()

  override fun addDirtyRegion(c: JComponent, x: Int, y: Int, w: Int, h: Int) {
    if (c === component) {
      regions.add(Rectangle(x, y, w, h))
    }
  }
}
