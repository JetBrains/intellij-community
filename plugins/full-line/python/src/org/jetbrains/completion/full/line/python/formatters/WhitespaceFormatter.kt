package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.completion.full.line.language.ElementFormatter
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase

class WhitespaceFormatter : ElementFormatter {
  private var skip = false

  override fun condition(element: PsiElement): Boolean = element is PsiWhiteSpace || skip

  override fun filter(element: PsiElement): Boolean? = null

  override fun format(element: PsiElement): String {
    if (skip) {
      skip = false
      return ""
    }

    if (element.prevSibling is PsiComment) {
      return ""
    }

    return if (isNewLine(element.text)) {
      fixEmptyLines(element.text)
    }
    else {
      if ("\\" in element.text) {
        skip = true
      }
      " "
    }
  }

  private fun isNewLine(text: String): Boolean {
    return text.matches(Regex("\\n\\s*"))
  }

  private fun fixEmptyLines(text: String): String {
    val char = CodeFormatterBase.TABULATION.first()

    var occurrences = 0
    for (ch in text.reversed()) {
      if (ch == char) {
        occurrences++
      }
      else {
        break
      }
    }

    return "\n" + "\t".repeat(occurrences / CodeFormatterBase.TABULATION.length)
  }
}
