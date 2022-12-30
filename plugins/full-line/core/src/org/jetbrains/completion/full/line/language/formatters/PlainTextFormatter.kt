package org.jetbrains.completion.full.line.language.formatters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.completion.full.line.language.ElementFormatter

class PlainTextFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = true

  override fun filter(element: PsiElement): Boolean? = element !is PsiFile && element.firstChild == null && element.text.isNotEmpty()

  override fun format(element: PsiElement): String = element.text
}
