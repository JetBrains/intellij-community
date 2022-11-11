package org.jetbrains.completion.full.line.language

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

interface ElementFormatter {
  fun condition(element: PsiElement): Boolean

  fun filter(element: PsiElement): Boolean? = null

  fun format(element: PsiElement): String

  fun formatFinalElement(element: PsiElement, range: TextRange): String? = null

  fun resetState(): Unit = Unit
}
