// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Font

@TestApplication
@UsePMarkerImplementation
internal class EditorFontChangeInlayTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `font changes refresh inlays in one batch`(changeFontName: Boolean): Unit = timeoutRunBlocking {
    checkFontChange(changeFontName)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @UsePMarkerImplementation(false)
  fun `font changes refresh legacy inlays in one batch`(changeFontName: Boolean): Unit = timeoutRunBlocking {
    checkFontChange(changeFontName)
  }

  private suspend fun checkFontChange(changeFontName: Boolean) {
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(DocumentImpl("a\nb\nc", true)) as EditorImpl
      try {
        var width = 20
        var height = 30
        val sizesDuringUpdate = ArrayList<Int>()
        val renderer = object : EditorCustomElementRenderer {
          override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            if (inlay.editor.inlayModel.isInBatchMode) {
              sizesDuringUpdate.add(inlay.editor.contentComponent.preferredSize.width)
            }
            return width
          }

          override fun calcHeightInPixels(inlay: Inlay<*>): Int = height
        }
        val model = editor.inlayModel
        val inline = model.addInlineElement(1, renderer)!!
        val block = model.addBlockElement(2, false, false, 0, renderer)
        assertThat(editor.offsetToXY(4).y).isEqualTo(2 * editor.lineHeight + height)
        val previousWidth = editor.contentComponent.preferredSize.width
        val updated = ArrayList<Inlay<*>>()
        var batchesStarted = 0
        var batchesFinished = 0
        model.addListener(object : InlayModel.Listener {
          override fun onBatchModeStart(editor: Editor) {
            batchesStarted++
          }

          override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
            assertThat(model.isInBatchMode).isTrue()
            updated.add(inlay)
          }

          override fun onBatchModeFinish(editor: Editor) {
            batchesFinished++
          }
        }, disposable)
        width += 1_000
        height += 13

        if (changeFontName) {
          val scheme = editor.colorsScheme
          scheme.editorFontName = if (scheme.editorFontName == Font.MONOSPACED) Font.SERIF else Font.MONOSPACED
        }
        else {
          editor.setFontSize(editor.colorsScheme.editorFontSize2D + 4)
        }

        assertThat(batchesStarted).isEqualTo(1)
        assertThat(batchesFinished).isEqualTo(1)
        assertThat(updated).containsExactlyInAnyOrder(inline, block)
        assertThat(sizesDuringUpdate).containsExactly(previousWidth, previousWidth)
        assertThat(model.isInBatchMode).isFalse()
        assertThat(inline.widthInPixels).isEqualTo(width)
        assertThat(block.widthInPixels).isEqualTo(width)
        assertThat(block.heightInPixels).isEqualTo(height)
        assertThat(editor.offsetToXY(4).y).isEqualTo(2 * editor.lineHeight + height)
        assertThat(editor.contentComponent.preferredSize.width).isGreaterThan(previousWidth).isGreaterThanOrEqualTo(width)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }
}
