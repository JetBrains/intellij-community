package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyClass
import org.jetbrains.completion.full.line.language.ElementFormatter
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.python.formatters.firstNextLeafWithText
import org.jetbrains.completion.full.line.python.formatters.handlePyArgumentList
import org.jetbrains.completion.full.line.python.formatters.intend

class ArgumentListFormatter(
  private val formatter: CodeFormatterBase,
  private val scopeInChar: Char,
  private val scopeOutChar: Char
) : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyArgumentList

  override fun filter(element: PsiElement): Boolean = element is PyArgumentList

  override fun format(element: PsiElement): String {
    element as PyArgumentList
    if (element.arguments.isEmpty()) {
      return if (element.parent is PyClass) "" else element.text
    }

    val lastComma = element.arguments.last().firstNextLeafWithText(",")
    val rootIntend = element.intend()
    val argIntend = element.arguments.first().intend()
    val closeParIntend = element.closingParen?.psi?.intend()

    return formatter.handlePyArgumentList(
      element.arguments,
      scopeInChar, scopeOutChar,
      lastComma != null,
      element.closingParen != null,
      (element.closingParen?.textLength ?: 0) * 2 + element.arguments.sumOf { it.textLength } >= 120,
      rootIntend == closeParIntend && argIntend - rootIntend >= 4,
    )
  }
}
