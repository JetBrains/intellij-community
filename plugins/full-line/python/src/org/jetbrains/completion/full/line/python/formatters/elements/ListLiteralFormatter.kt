package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyListLiteralExpression
import org.jetbrains.completion.full.line.language.ElementFormatter

class ListLiteralFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyListLiteralExpression

  override fun filter(element: PsiElement): Boolean = element.parent is PyListLiteralExpression

  override fun format(element: PsiElement): String {
    return element.text.replace("\n", " ")
  }
}
