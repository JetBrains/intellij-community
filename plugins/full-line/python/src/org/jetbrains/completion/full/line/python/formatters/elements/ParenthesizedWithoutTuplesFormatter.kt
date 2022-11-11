package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyTupleExpression
import org.jetbrains.completion.full.line.language.ElementFormatter
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.language.formatters.PlainTextFormatter
import org.jetbrains.completion.full.line.python.formatters.firstNextLeafWithText
import org.jetbrains.completion.full.line.python.formatters.handlePyArgumentList
import org.jetbrains.completion.full.line.python.formatters.intend

class ParenthesizedWithoutTuplesFormatter(
  private val formatter: CodeFormatterBase,
  private val scopeInChar: Char,
  private val scopeOutChar: Char
) : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyParenthesizedExpression

  override fun filter(element: PsiElement): Boolean = element is PyParenthesizedExpression

  override fun format(element: PsiElement): String {
    element as PyParenthesizedExpression

    val children = element.children.filterIsInstance<PyTupleExpression>()

    return when {
      children.isNotEmpty() -> {
        children.joinToString {
          val lastComma = it.elements.last().firstNextLeafWithText(",")
          val rootIntend = element.intend()
          val argIntend = it.elements.first().intend()
          val lastBracket = it.firstNextLeafWithText(")")

          formatter.handlePyArgumentList(
            it.elements,
            scopeInChar, scopeOutChar,
            lastComma != null,
            lastBracket != null,
            it.elements.sumOf { el -> el.textLength } + it.elements.first().intend() >= 120,
            argIntend - rootIntend >= 4,
          )
        }
      }
      element.containedExpression != null -> element.containedExpression?.let {
        formatter.formatterOrNull(it, PlainTextFormatter::class)?.format(it)
      } ?: element.text
      else -> {
        ""
      }
    }
  }
}
