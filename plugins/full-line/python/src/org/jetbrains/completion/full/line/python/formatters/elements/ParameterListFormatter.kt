package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyStringLiteralCoreUtil
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import org.jetbrains.completion.full.line.language.ElementFormatter
import org.jetbrains.completion.full.line.python.formatters.firstNextLeafWithText
import org.jetbrains.completion.full.line.python.formatters.handlePyArgumentList
import org.jetbrains.completion.full.line.python.formatters.intend

class ParameterListFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyParameterList

  override fun filter(element: PsiElement): Boolean = element is PyParameterList

  override fun format(element: PsiElement): String {
    element as PyParameterList
    if (element.text.isEmpty()) {
      return ""
    }

    val params = ContainerUtil.map(element.parameters, PyCallableParameterImpl::psi)

    val rootIntend = element.intend()
    val argIntend = params.firstOrNull()?.declarationElement?.intend() ?: 0
    val closeParIntend = element.lastChild?.intend()
    val lastComma = params.lastOrNull()?.declarationElement?.firstNextLeafWithText(",")
    val lastBracket = element.text.lastOrNull { it == ')' }

    return handlePyArgumentList(
      params,
      '⇥', '⇤',
      lastComma != null,
      lastBracket != null,
      element.text.filter { !it.isWhitespace() }.length >= 120,
      rootIntend == closeParIntend && argIntend - rootIntend >= 4,
    ) {
      if (it.hasDefaultValue()) {
        val whiteSpace = if (it.defaultValueText == "False") "" else " "

        it.getPresentableText(false) +
        ((it.parameter as PyNamedParameter).annotation?.text ?: "") + whiteSpace +
        includeDefaultValue(whiteSpace + it.defaultValueText!!)
      }
      else {
        if (it.parameter is PyNamedParameter) {
          it.getPresentableText(true) + ((it.parameter as PyNamedParameter).annotation?.text ?: "")
        }
        else {
          "*"
        }
      }
    }
  }

  private fun includeDefaultValue(defaultValue: String): String {
    val sb = StringBuilder()
    val quotes = PyStringLiteralCoreUtil.getQuotes(defaultValue)

    sb.append("=")
    if (quotes != null) {
      val value: String = defaultValue.substring(quotes.getFirst().length, defaultValue.length - quotes.getSecond().length)
      sb.append(quotes.getFirst())
      StringUtil.escapeStringCharacters(value.length, value, sb)
      sb.append(quotes.getSecond())
    }
    else {
      sb.append(defaultValue)
    }

    return sb.toString()
  }
}
