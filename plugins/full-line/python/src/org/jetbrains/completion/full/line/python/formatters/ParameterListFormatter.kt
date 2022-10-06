package org.jetbrains.completion.full.line.python.formatters

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyStringLiteralCoreUtil
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import org.jetbrains.completion.full.line.language.ElementFormatter

class ParameterListFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyParameterList

  override fun filter(element: PsiElement): Boolean? = element is PyParameterList

  override fun format(element: PsiElement): String {
    element as PyParameterList
    return if (element.text.isNotEmpty()) {
      val closing = if (element.text.last() == ')') ")" else ""
      val params = ContainerUtil.map(element.parameters, PyCallableParameterImpl::psi)
      "(" + formatParameter(params) + closing
    }
    else {
      ""
    }
  }

  private fun formatParameter(params: List<PyCallableParameter>): String {
    return params.joinToString(separator = ", ") {
      if (it.hasDefaultValue()) {
        it.getPresentableText(false) + ((it.parameter as PyNamedParameter).annotation?.text ?: "") +
        includeDefaultValue(it.defaultValueText!!)
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
