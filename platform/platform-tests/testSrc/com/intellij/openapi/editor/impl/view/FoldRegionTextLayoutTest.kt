// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
@UsePMarkerImplementation
internal class FoldRegionTextLayoutTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `font changes refresh fold layouts`(expanded: Boolean): Unit = timeoutRunBlocking {
    withEditor { editor ->
      val model = editor.foldingModel
      val region = addFoldRegion(editor, 0, 5, "placeholder")
      model.runBatchFoldingOperation { region.isExpanded = false }
      val layout = editor.view.getFoldRegionLayout(region)
      val width = layout.width
      assertSame(layout, editor.view.getFoldRegionLayout(region))
      model.runBatchFoldingOperation { region.isExpanded = expanded }

      editor.setFontSize(editor.colorsScheme.editorFontSize2D + 4)
      model.runBatchFoldingOperation { region.isExpanded = false }

      val updated = editor.view.getFoldRegionLayout(region)
      assertNotSame(layout, updated)
      assertTrue(updated.width > width)
      assertSame(updated, editor.view.getFoldRegionLayout(region))
    }
  }

  @Test
  fun `placeholder changes refresh only the changed fold layout`(): Unit = timeoutRunBlocking {
    withEditor { editor ->
      val model = editor.foldingModel
      val first = addFoldRegion(editor, 0, 5, ".")
      val second = addFoldRegion(editor, 6, 12, ".")
      model.runBatchFoldingOperation {
        first.isExpanded = false
        second.isExpanded = false
      }
      val firstLayout = editor.view.getFoldRegionLayout(first)
      val secondLayout = editor.view.getFoldRegionLayout(second)

      model.runBatchFoldingOperation { first.placeholderText = "longer placeholder" }

      val updated = editor.view.getFoldRegionLayout(first)
      assertNotSame(firstLayout, updated)
      assertTrue(updated.width > firstLayout.width)
      assertSame(secondLayout, editor.view.getFoldRegionLayout(second))
    }
  }

  private fun addFoldRegion(editor: EditorImpl, start: Int, end: Int, placeholder: String): FoldRegion {
    lateinit var region: FoldRegion
    editor.foldingModel.runBatchFoldingOperation {
      region = editor.foldingModel.addFoldRegion(start, end, placeholder)!!
    }
    return region
  }

  private suspend fun withEditor(action: (EditorImpl) -> Unit) {
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(DocumentImpl("first\nsecond", true)) as EditorImpl
      try {
        action(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }
}
