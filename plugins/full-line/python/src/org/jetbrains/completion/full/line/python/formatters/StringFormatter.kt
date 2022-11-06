package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.python.psi.PyStringElement
import org.jetbrains.completion.full.line.language.ElementFormatter

class StringFormatter : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = element is PyStringElement

  override fun filter(element: PsiElement): Boolean? {
    return element is LeafPsiElement && element.parent !is PyStringElement || element is PyStringElement
  }

  override fun format(element: PsiElement): String {
    element as PyStringElement

    val quote = if (element.isTripleQuoted) {
      TRIPLE_QUOTE
    }
    else {
      fixQuote(element.content)
    }

    val content = if (element.prefix.contains("r")) {
      element.content
    }
    else {
      unescapedNewQuote.replace(element.content, SINGLE_QUOTE)
    }
    return element.prefix + quote + content + quote
  }

  private fun fixQuote(content: String): String {
    return if (content.contains("\"") && !content.contains("\\\"")) SINGLE_QUOTE else DOUBLE_QUOTE
  }

  companion object {
    const val TRIPLE_QUOTE = "\"\"\""
    const val SINGLE_QUOTE = "\'"
    const val DOUBLE_QUOTE = "\""

    val unescapedNewQuote = Regex("(([\\\\])(^\\\\\\\\)*)$SINGLE_QUOTE")
  }
}
