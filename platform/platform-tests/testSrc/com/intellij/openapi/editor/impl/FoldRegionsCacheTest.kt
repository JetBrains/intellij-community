// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import com.intellij.util.DocumentUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

@Suppress("SuspiciousPackagePrivateAccess")
@TestApplication
@UsePMarkerImplementation
internal class FoldRegionsCacheTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `font changes preserve the cached fold visibility`(changeFontName: Boolean): Unit = timeoutRunBlocking {
    checkFontChangePreservesFoldVisibility(changeFontName)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @UsePMarkerImplementation(false)
  fun `font changes preserve the cached fold visibility with legacy markers`(changeFontName: Boolean): Unit = timeoutRunBlocking {
    checkFontChangePreservesFoldVisibility(changeFontName)
  }

  private suspend fun checkFontChangePreservesFoldVisibility(changeFontName: Boolean) {
    withEditor { editor ->
      val model = editor.foldingModel
      lateinit var outer: FoldRegion
      lateinit var inner: FoldRegion
      lateinit var later: FoldRegion
      model.runBatchFoldingOperation {
        outer = model.addFoldRegion(0, 7, "outer")!!
        inner = model.addFoldRegion(2, 5, "inner")!!
        later = model.addFoldRegion(10, 13, "later")!!
        inner.isExpanded = false
        outer.isExpanded = false
        later.isExpanded = false
      }
      val visible = model.fetchVisible()
      assertThat(model.totalNumberOfFoldedLines).isEqualTo(4)

      if (changeFontName) {
        val scheme = editor.colorsScheme
        scheme.editorFontName = if (scheme.editorFontName == Font.MONOSPACED) Font.SERIF else Font.MONOSPACED
      }
      else {
        editor.setFontSize(editor.colorsScheme.editorFontSize2D + 4)
      }

      assertThat(model.fetchVisible()).isSameAs(visible).containsExactly(later, outer)
      assertThat(model.fetchTopLevel()).containsExactly(outer, later)
      assertThat(outer.textRange).isEqualTo(TextRange(0, 7))
      assertThat(inner.textRange).isEqualTo(TextRange(2, 5))
      assertThat(later.textRange).isEqualTo(TextRange(10, 13))
      assertThat(model.getCollapsedRegionAtOffset(3)).isSameAs(outer)
      assertThat(model.totalNumberOfFoldedLines).isEqualTo(4)
      assertThat(model.getLogicalLineForVisualLineWithoutSoftWraps(3)).isEqualTo(7)
      assertThat(editor.offsetToVisualLine(14)).isEqualTo(3)

      model.runBatchFoldingOperation { outer.isExpanded = true }

      assertThat(model.fetchTopLevel()).containsExactly(inner, later)
      assertThat(model.getCollapsedRegionAtOffset(3)).isSameAs(inner)
      assertThat(model.totalNumberOfFoldedLines).isEqualTo(2)
      assertThat(editor.offsetToVisualLine(14)).isEqualTo(5)
    }
  }

  @Test
  fun `general settings changes rebuild fold visibility`(): Unit = timeoutRunBlocking {
    withEditor { editor ->
      val model = editor.foldingModel
      lateinit var region: FoldRegion
      model.runBatchFoldingOperation {
        region = model.addFoldRegion(0, 5, "...")!!
        region.isExpanded = false
      }
      val visible = model.fetchVisible()

      editor.reinitSettings()

      assertThat(model.fetchVisible()).isNotSameAs(visible).containsExactly(region)
      assertThat(model.totalNumberOfFoldedLines).isEqualTo(2)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `font changes refresh custom fold heights`(changeHeight: Boolean): Unit = timeoutRunBlocking {
    withEditor { editor ->
      val model = editor.foldingModel
      val firstRenderer = FoldRenderer(editor.lineHeight * 3)
      val secondRenderer = FoldRenderer(editor.lineHeight * 4)
      lateinit var first: CustomFoldRegion
      lateinit var second: CustomFoldRegion
      model.runBatchFoldingOperation {
        first = model.addCustomLinesFolding(0, 1, firstRenderer)!!
        model.addFoldRegion(4, 7, "...")!!.isExpanded = false
        second = model.addCustomLinesFolding(4, 5, secondRenderer)!!
      }
      val visible = model.fetchVisible()
      assertThat(editor.offsetToXY(12).y).isEqualTo(first.heightInPixels + editor.lineHeight + second.heightInPixels)
      if (changeHeight) {
        firstRenderer.height += 37
      }

      editor.setFontSize(editor.colorsScheme.editorFontSize2D + 4)

      assertThat(model.fetchVisible()).isSameAs(visible)
      assertThat(first.heightInPixels).isEqualTo(firstRenderer.height)
      assertThat(second.heightInPixels).isEqualTo(secondRenderer.height)
      assertThat(editor.offsetToXY(12).y).isEqualTo(first.heightInPixels + editor.lineHeight + second.heightInPixels)
      val topLevel = model.fetchTopLevel()

      firstRenderer.height += 11
      first.update()

      assertThat(model.fetchTopLevel()).isSameAs(topLevel)
      assertThat(editor.offsetToXY(12).y).isEqualTo(first.heightInPixels + editor.lineHeight + second.heightInPixels)
    }
  }

  @Test
  fun `font changes refresh folded block inlay heights`(): Unit = timeoutRunBlocking {
    withEditor { editor ->
      var extraHeight = 3
      val renderer = object : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int = 10

        override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlay.editor.lineHeight + extraHeight
      }
      val hidden = editor.inlayModel.addBlockElement(2, false, false, 0, renderer)
      val visible = editor.inlayModel.addBlockElement(6, false, false, 0, renderer)
      val model = editor.foldingModel
      model.runBatchFoldingOperation {
        model.addFoldRegion(0, 5, "...")!!.isExpanded = false
      }
      val visibleRegions = model.fetchVisible()
      assertThat(model.totalHeightOfFoldedBlockInlays).isEqualTo(hidden.heightInPixels)
      assertThat(editor.offsetToXY(8).y).isEqualTo(2 * editor.lineHeight + visible.heightInPixels)
      extraHeight += 7

      editor.setFontSize(editor.colorsScheme.editorFontSize2D + 4)

      assertThat(model.fetchVisible()).isSameAs(visibleRegions)
      assertThat(hidden.heightInPixels).isEqualTo(editor.lineHeight + extraHeight)
      assertThat(model.totalHeightOfFoldedBlockInlays).isEqualTo(hidden.heightInPixels)
      assertThat(editor.offsetToXY(8).y).isEqualTo(2 * editor.lineHeight + visible.heightInPixels)

      extraHeight += 5
      hidden.update()
      visible.update()

      assertThat(model.totalHeightOfFoldedBlockInlays).isEqualTo(hidden.heightInPixels)
      assertThat(editor.offsetToXY(8).y).isEqualTo(2 * editor.lineHeight + visible.heightInPixels)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `font changes use current ranges after edits with folding disabled`(bulk: Boolean): Unit = timeoutRunBlocking {
    withEditor { editor ->
      val model = editor.foldingModel
      lateinit var region: FoldRegion
      model.runBatchFoldingOperation {
        region = model.addFoldRegion(2, 7, "...")!!
        region.isExpanded = false
      }
      val topLevel = model.fetchTopLevel()
      assertThat(model.totalNumberOfFoldedLines).isEqualTo(2)
      model.isFoldingEnabled = false
      editor.reinitSettings()
      edtWriteAction {
        val document = editor.elfDocument
        DocumentUtil.executeInBulk(document, bulk) {
          document.insertString(0, "prefix\n")
        }
      }
      model.isFoldingEnabled = true

      editor.setFontSize(editor.colorsScheme.editorFontSize2D + 4)

      assertThat(model.fetchTopLevel()).isNotSameAs(topLevel).containsExactly(region)
      assertThat(region.textRange).isEqualTo(TextRange(9, 14))
      assertThat(model.getCollapsedRegionAtOffset(10)).isSameAs(region)
      assertThat(model.getCollapsedRegionAtOffset(3)).isNull()
      assertThat(model.totalNumberOfFoldedLines).isEqualTo(2)
      assertThat(editor.offsetToVisualLine(21)).isEqualTo(6)
      assertThat(model.getLogicalLineForVisualLineWithoutSoftWraps(6)).isEqualTo(8)
    }
  }

  private suspend fun withEditor(action: suspend (EditorImpl) -> Unit) {
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(DocumentImpl("a\nb\nc\nd\ne\nf\ng\nh", true)) as EditorImpl
      try {
        action(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }

  private class FoldRenderer(var height: Int) : CustomFoldRegionRenderer {
    override fun calcWidthInPixels(region: CustomFoldRegion): Int = 10

    override fun calcHeightInPixels(region: CustomFoldRegion): Int = height

    override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {}
  }
}
