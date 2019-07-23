// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestFileType
import org.junit.Test
import java.awt.Dimension
import java.awt.Point


class PopupUtilTest : AbstractEditorTest() {

  @Test
  fun `test popups positions inside editor`() {
    init("""
      import org.junit.Test
      class TestCase {
        @Test fun tes<caret>t() {}
      }
    """.trimIndent(), TestFileType.TEXT)
    val editor = editor as EditorEx
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
    assertEquals(editor.lineHeight, popupPosition.point.y - balloonPosition.point.y)
    assertEquals(0, popupPosition.point.x - balloonPosition.point.x)
    assertEquals(xyBalloonPosition, balloonPosition.point)
    assertEquals(xyPopupPosition, popupPosition.point)
  }
}