package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyConditionalExpression
import org.jetbrains.completion.full.line.language.ElementFormatter
import org.jetbrains.completion.full.line.python.formatters.firstNextLeafWithText
import org.jetbrains.completion.full.line.python.formatters.firstPrevLeafWithText
import org.jetbrains.completion.full.line.python.formatters.intend

class ConditionalFormatter(
  private val scopeInChar: Char,
  private val scopeOutChar: Char
) : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyConditionalExpression

  override fun filter(element: PsiElement): Boolean = element is PyConditionalExpression

  override fun format(element: PsiElement): String = with(element as PyConditionalExpression) {
    if (textLength < 120) return text

    val lastComma = firstPrevLeafWithText("(")?.intend() ?: 0
    val rootIntend = intend()
    val argIntend = firstNextLeafWithText(")")?.intend() ?: 0

    val intend = lastComma == argIntend && (rootIntend - lastComma) >= 4

    val prefix = if (intend) "($scopeInChar" else "("
    val postfix = if (intend) "$scopeOutChar)" else ")"

    text.split("\n").joinToString("\n", prefix, postfix) {
      it.trim()
    }
  }
}
