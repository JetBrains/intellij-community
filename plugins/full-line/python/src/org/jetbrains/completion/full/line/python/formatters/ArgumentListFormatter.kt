package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import org.jetbrains.completion.full.line.language.ElementFormatter

class ArgumentListFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyArgumentList

  override fun filter(element: PsiElement): Boolean? = element is PyArgumentList

  override fun format(element: PsiElement): String {
    element as PyArgumentList

    return if (element.arguments.isEmpty()) {
      if (element.parent is PyClass) {
        ""
      }
      else {
        element.text
      }
    }
    else {
      handlePyArgumentList(element.arguments, element.text.last() == ',', element.closingParen != null)
    }
  }

  companion object {
    fun handlePyArgumentList(arguments: Array<PyExpression>, lastComma: Boolean = false, closed: Boolean = true): String {
      val text = StringBuilder("(")
      arguments.forEach {
        text.append(it.text).append(", ")
      }
      if (!lastComma) {
        text.delete(text.length - 2, text.length)
      }
      else {
        text.delete(text.length - 1, text.length)
      }
      if (closed) {
        text.append(")")
      }
      return text.toString()
    }
  }
}
