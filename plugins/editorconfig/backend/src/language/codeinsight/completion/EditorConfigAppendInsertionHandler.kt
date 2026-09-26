// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.openapi.editor.ScrollType
import com.intellij.psi.PsiFile

class EditorConfigAppendInsertionHandler(
  private val suffix: String,
  private val shouldMoveCaret: Boolean = true,
  private val shouldAutopopup: Boolean = true
) : InsertHandler<LookupElement> {

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val document = context.document
    val editor = context.editor

    val caretModel = editor.caretModel
    val caretOffset = caretModel.offset

    document.insertString(caretOffset, suffix)

    if (shouldMoveCaret) {
      caretModel.moveToOffset(caretOffset + suffix.length)
      editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }
    editor.selectionModel.removeSelection()

    if (shouldAutopopup) {
      AutoPopupController.getInstance(editor.project!!).scheduleAutoPopup(editor)
    }
  }
}

internal fun LookupElementBuilder.withSuffix(text: String): LookupElementBuilder =
  withInsertHandler(EditorConfigAppendInsertionHandler(text))

internal fun LookupElementBuilder.withSeparatorIn(file: PsiFile) = withSuffix(getSeparatorInFile(file))

fun getSeparatorInFile(file: PsiFile): String {
  val settings = CodeStyle.getLanguageSettings(file, EditorConfigLanguage)
  val needSpace = settings.SPACE_AROUND_ASSIGNMENT_OPERATORS
  return if (needSpace) " = " else "="
}
