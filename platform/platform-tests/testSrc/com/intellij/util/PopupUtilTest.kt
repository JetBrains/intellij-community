// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestFileType
import org.junit.Test
import java.applet.Applet
import java.awt.*


class PopupUtilTest : AbstractEditorTest() {

  @Test
  fun `test popups positions inside editor`() {
    init("""
      import org.junit.Test
      class TestCase {
        @Test fun tes<caret>t() {}
      }
    """.trimIndent(), TestFileType.TEXT)
    val editor = myEditor as EditorEx
    editor.scrollPane.viewport.apply {
      viewPosition = Point(0, 0)
      extentSize = Dimension(500, 1000)
    }
    val logicalPosition = editor.caretModel.logicalPosition
    val context = MapDataContext().apply {
      put(PlatformDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
      put(CommonDataKeys.EDITOR, editor)
    }
    val xyBalloonPosition = editor.logicalPositionToXY(logicalPosition)
    val xyPopupPosition = Point(xyBalloonPosition.x, xyBalloonPosition.y + editor.lineHeight)
    val balloonPosition = getBestBalloonPosition(context)
    val popupPosition = getBestPopupPosition(context)
    assertEquals(xyBalloonPosition, balloonPosition.point)
    assertEquals(xyPopupPosition, popupPosition.point)
  }

  @Test
  fun `test popups positions inside gutter`() {
    init("""
      import org.junit.Test
      class TestCase {
        @Test fun tes<caret>t() {}
      }
    """.trimIndent(), TestFileType.TEXT)
    val editor = myEditor as EditorEx
    editor.gutterComponentEx.forEachComponentFromHierarchy {
      bounds = Rectangle(0, 0, 100, 1000)
    }
    val logicalPosition = editor.caretModel.logicalPosition
    val context = MapDataContext().apply {
      put(PlatformDataKeys.CONTEXT_COMPONENT, editor.gutterComponentEx)
      put(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR, logicalPosition.line)
      put(CommonDataKeys.EDITOR, editor)
    }
    val xyBalloonPosition = Point(50, editor.logicalPositionToXY(logicalPosition).y)
    val xyPopupPosition = Point(50, xyBalloonPosition.y + editor.lineHeight)
    val balloonPosition = getBestBalloonPosition(context)
    val popupPosition = getBestPopupPosition(context)
    assertEquals(xyBalloonPosition, balloonPosition.point)
    assertEquals(xyPopupPosition, popupPosition.point)
  }

  private fun Component.forEachComponentFromHierarchy(apply: Component.() -> Unit) {
    val parent = parent
    if (parent != null && parent !is Window && parent !is Applet) {
      parent.forEachComponentFromHierarchy(apply)
    }
    apply()
  }
}