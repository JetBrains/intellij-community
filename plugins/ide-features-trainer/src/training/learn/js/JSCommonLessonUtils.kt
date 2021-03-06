// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.js

import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.components.fields.ExtendableTextField
import training.dsl.TaskRuntimeContext

fun TaskRuntimeContext.textBeforeCaret(text: String): Boolean {
  val offset = editor.caretModel.offset
  return textBeforeOffset(offset, text)
}

fun TaskRuntimeContext.textBeforeOffset(offset: Int, text: String): Boolean {
  if (offset < text.length) return false
  val subSequence = editor.document.charsSequence.subSequence(offset - text.length, offset)
  return subSequence.toString() == text
}

fun TaskRuntimeContext.textAfterOffset(offset: Int, text: String): Boolean {
  val charsSequence = editor.document.charsSequence
  if (offset + text.length > charsSequence.length) return false
  val subSequence = charsSequence.subSequence(offset, offset + text.length)
  return subSequence.toString() == text
}

fun TaskRuntimeContext.textOnLine(line: Int, text: String): Boolean {
  val lineStartOffset = editor.document.getLineStartOffset(line)
  val lineEndOffset = editor.document.getLineEndOffset(line)
  val subSequence = editor.document.charsSequence.subSequence(lineStartOffset, lineEndOffset)
  return subSequence.toString().contains(text)
}

fun TaskRuntimeContext.findElementAtCaret(): PsiElement? {
  val caret = editor.caretModel
  val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
  val offset = caret.offset
  return psiFile.findElementAt(offset)
}

fun TaskRuntimeContext.findTextAtCaret(): String? {
  val element = findElementAtCaret()
  return element?.text
}

fun TaskRuntimeContext.textAtCaretEqualsTo(text: String): Boolean {
  val foundText = findTextAtCaret()
  return text == foundText || text == "("
}

fun shiftSymbol(): String {
  if (SystemInfo.isMac) {
    return "⇧"
  }
  return "Shift"
}


fun TaskRuntimeContext.checkWordInSearchEverywhereInput(expected: String): Boolean =
  (focusOwner as? ExtendableTextField)?.text?.toLowerCase()?.contains(expected.toLowerCase()) == true