package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyNumericLiteralExpression
import org.jetbrains.completion.full.line.language.ElementFormatter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class NumericalFormatter(private val allowUnderscores: Boolean) : ElementFormatter {
  private val formatter = DecimalFormat("")
  private val symbols: DecimalFormatSymbols

  init {
    symbols = formatter.decimalFormatSymbols.apply { groupingSeparator = '_' }
    formatter.decimalFormatSymbols = symbols
  }

  override fun condition(element: PsiElement): Boolean = element.parent is PyNumericLiteralExpression

  override fun filter(element: PsiElement): Boolean? = null

  override fun format(element: PsiElement): String {
    // No underscores for numbers <= 5 digits long.
    var coreSign = ""
    val text = element.text.toLowerCase().let { s ->
      if (s[0] == '-') {
        coreSign = "-"
        s.drop(1)
      }
      else {
        s
      }
    }
    val a = when {
      text.startsWith("0o") || text.startsWith("0b") -> {
        text
      }
      text.startsWith("0x") -> {
        val parts = text.split("x")
        parts[0] + 'x' + parts[1].toUpperCase()
      }
      "e" in text -> {
        val parts = text.split("e")
        var after = parts[1]
        var sign = ""
        if (after.startsWith('-')) {
          after = after.drop(1)
          sign = "-"
        }
        else if (after.startsWith('+')) {
          after = after.drop(1)
        }
        formatFloatOrInt(parts[0], allowUnderscores) + 'e' + sign + formatInt(after, allowUnderscores).toUpperCase()
      }
      else -> {
        formatFloatOrInt(text, allowUnderscores)
      }
    }
    return coreSign + a
  }

  private fun formatFloatOrInt(text: String, allowUnderscores: Boolean): String {
    if (!text.contains('.')) {
      return formatInt(text, allowUnderscores)
    }

    val parts = text.split(".")
    val before = formatInt(parts[0], allowUnderscores)
    val after = if (parts[1].isEmpty()) {
      "0"
    }
    else {
      formatInt(parts[1], allowUnderscores)
    }

    return "$before.$after"
  }

  private fun formatInt(text: String, allowUnderscores: Boolean): String {
    if (!allowUnderscores) {
      return text
    }

    val s = text.replace("_", "")
    if (s.length <= 5) {
      return text
    }

    return StringBuilder().apply {
      var uCount = 0
      for (c in s.reversed()) {
        if ((length - uCount) % 3 == 0 && length != 0) {

          append('_')
          uCount++
        }
        append(c)
      }
    }.toString()
  }
}
