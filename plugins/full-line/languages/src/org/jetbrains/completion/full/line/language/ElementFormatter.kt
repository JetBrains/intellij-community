package org.jetbrains.completion.full.line.language

import com.intellij.psi.PsiElement

interface ElementFormatter {
  fun condition(element: PsiElement): Boolean

  fun filter(element: PsiElement): Boolean?

  fun format(element: PsiElement): String
}
