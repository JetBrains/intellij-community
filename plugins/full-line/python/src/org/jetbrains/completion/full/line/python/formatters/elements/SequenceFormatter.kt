package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PySequenceExpression
import org.jetbrains.completion.full.line.language.ElementFormatter
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.python.formatters.firstNextLeafWithText
import org.jetbrains.completion.full.line.python.formatters.handlePyArgumentList
import org.jetbrains.completion.full.line.python.formatters.intend

class SequenceFormatter(
  private val formatter: CodeFormatterBase,
  private val scopeInChar: Char,
  private val scopeOutChar: Char
) : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PySequenceExpression && element.elements.isNotEmpty() && element !is PyListLiteralExpression

  override fun filter(element: PsiElement) = null

  override fun format(element: PsiElement): String {
    element as PySequenceExpression

    val lastComma = element.elements.lastOrNull()?.firstNextLeafWithText(",")
    val rootIntend = element.intend()
    val argIntend = element.elements.firstOrNull()?.intend() ?: 0

    return formatter.handlePyArgumentList(
      element.elements,
      scopeInChar, scopeOutChar,
      lastComma != null,
      false,
      element.elements.sumOf { it.textLength } >= 120,
      argIntend - rootIntend >= 4,
    )
  }
}
