// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.editor.actions

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGSTRING_LITERAL
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTRING_LITERAL

class GroovyTripleQuoteBackspaceHandlerDelegate : BackspaceHandlerDelegate() {

  private var myWithinTripleQuoted: Boolean = false

  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
    myWithinTripleQuoted = false
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return
    if (c != '\'' && c != '"') return
    editor as? EditorEx ?: return
    val offset = editor.caretModel.offset
    val iterator: HighlighterIterator = editor.highlighter.createIterator(offset)
    val tokenType = iterator.tokenType
    if (tokenType == mSTRING_LITERAL || tokenType == mGSTRING_LITERAL) {
      myWithinTripleQuoted = iterator.start + 3 == offset && iterator.end - 3 == offset
    }
  }

  override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
    if (!myWithinTripleQuoted) return false
    val offset = editor.caretModel.offset
    editor.document.deleteString(offset, offset + 3)
    return true
  }
}