package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyListLiteralExpression
import org.jetbrains.completion.full.line.language.ElementFormatter

class ListLiteralFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element.parent is PyListLiteralExpression

  override fun filter(element: PsiElement): Boolean? = null

  override fun format(element: PsiElement): String {
    return element.text.replace(" ", "").replace("\n", " ")
  }
}
