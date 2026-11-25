// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.LightweightHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Point
import javax.swing.JLabel

@TestApplication
internal class LocalHintManagerTest {
  @Test
  fun `reposition a question hint after the document shrinks`(): Unit = timeoutRunBlocking {
    val beforeShow = LightweightHint::class.java.getDeclaredMethod("fireBeforeShow")
    beforeShow.isAccessible = true

    withContext(Dispatchers.EDT) {
      val editorFactory = EditorFactory.getInstance()
      val document = editorFactory.createDocument("keep remove")
      val editor = editorFactory.createEditor(document)
      val hint = LightweightHint(JLabel())
      val hintManager = ClientHintManager.getCurrentInstance()
      try {
        hintManager.showQuestionHint(editor, Point(), 5, document.textLength, null, hint, 0, { false }, HintManager.ABOVE)
        beforeShow.invoke(hint)
        assertThat(editor.markupModel.allHighlighters).hasSize(1)

        runWriteAction {
          CommandProcessor.getInstance().runUndoTransparentAction {
            document.replaceString(5, document.textLength, "x")
          }
        }
        hint.hide()
        beforeShow.invoke(hint)
        assertThat(editor.markupModel.allHighlighters).isEmpty()
      }
      finally {
        hint.hide()
        hintManager.cleanup()
        editorFactory.releaseEditor(editor)
      }
    }
  }
}
