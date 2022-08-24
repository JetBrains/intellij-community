package org.jetbrains.completion.full.line.language

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

interface PsiCodeFormatter {
  data class FormatResult(val context: String, val rollbackPrefix: List<String>)

  fun format(psiFile: PsiFile, position: PsiElement, offset: Int): FormatResult
}
