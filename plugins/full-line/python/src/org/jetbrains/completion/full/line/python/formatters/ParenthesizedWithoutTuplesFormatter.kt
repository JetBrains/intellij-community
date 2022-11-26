package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyTupleExpression
import org.jetbrains.completion.full.line.language.ElementFormatter

class ParenthesizedWithoutTuplesFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyParenthesizedExpression

  override fun filter(element: PsiElement): Boolean? = element is PyParenthesizedExpression

  override fun format(element: PsiElement): String {
    element as PyParenthesizedExpression

    val children = element.children.filterIsInstance<PyTupleExpression>()

    return when {
      children.isNotEmpty() -> {
        children.joinToString { ArgumentListFormatter.handlePyArgumentList(it.elements) }
      }
      element.containedExpression != null -> {
        spacesToOne(element.containedExpression!!.text.trim().replace(Regex("[\n\\\\]"), ""))
      }
      else -> {
        ""
      }
    }
  }

  private fun spacesToOne(text: String): String {
    return text.replace(Regex(" +"), " ")
  }
}
