package org.jetbrains.completion.full.line.language

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

interface CodeFormatter {
  fun format(element: PsiElement, range: TextRange, editor: Editor): String
}
