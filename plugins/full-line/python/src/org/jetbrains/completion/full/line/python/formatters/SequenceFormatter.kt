package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PySequenceExpression
import org.jetbrains.completion.full.line.language.ElementFormatter

class SequenceFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PySequenceExpression && element.elements.isNotEmpty()

  override fun filter(element: PsiElement): Boolean? = null

  override fun format(element: PsiElement): String {
    element as PySequenceExpression

    return ArgumentListFormatter.handlePyArgumentList(element.elements)
  }
}
