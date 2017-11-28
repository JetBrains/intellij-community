/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  override fun beforeCharDeleted(c: Char, file: PsiFile?, editor: Editor?) {
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

  override fun charDeleted(c: Char, file: PsiFile?, editor: Editor?): Boolean {
    if (!myWithinTripleQuoted || editor == null) return false
    val offset = editor.caretModel.offset
    editor.document.deleteString(offset, offset + 3)
    return true
  }
}