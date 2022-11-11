package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.python.psi.PyStringElement
import org.jetbrains.completion.full.line.language.ElementFormatter
import kotlin.math.abs
import kotlin.math.min

class StringFormatter(private val scopeInChar: Char, private val scopeOutChar: Char) : ElementFormatter {

  override fun condition(element: PsiElement): Boolean = element is PyStringElement

  override fun filter(element: PsiElement): Boolean {
    return element is LeafPsiElement && element.parent !is PyStringElement || element is PyStringElement
  }

  override fun format(element: PsiElement): String = with(element as PyStringElement) {
    val quote = formatQuote()
    val prefix = formatPrefix()
    val content = formatContent(prefix, content)

    return prefix + quote + content + quote
  }

  override fun formatFinalElement(element: PsiElement, range: TextRange): String {
    val str = StringBuilder()
    with(element as PyStringElement) {
      val textRange = TextRange(0, min(range.endOffset, textRange.endOffset) - textRange.startOffset)

      val quote = formatQuote().also {
        if (textRange.length >= it.length) str.append(it)
      }
      val prefix = formatPrefix().also {
        if (textRange.length >= str.length + it.length) str.append(it)
      }

      formatContent(
        prefix,
        TextRange(textRange.startOffset, min(textRange.endOffset - str.length, content.length))
          .substring(content)
      ).also {
        if (textRange.length >= str.length + it.length) str.append(it)
      }
      if (textRange.length >= str.length + quote.length) str.append(quote)
    }
    return str.toString()
  }

  private fun PyStringElement.formatContent(prefix: String, content: String): String {
    var fixedContent = if (prefix.contains("r")) content else unescapedNewQuote.replace(content, SINGLE_QUOTE)
    if (!isTripleQuoted) return fixedContent

    fixedContent = if (fixedContent.contains("\n")) fixedContent(fixedContent).trimIndent() else fixedContent(fixedContent)

    val scopeIn = if (!fixedContent.startsWith(scopeInChar)) "\n" else ""
    val scopeOut = if (!fixedContent.endsWith(scopeOutChar)) "\n" else ""

    return scopeIn + fixedContent + scopeOut
  }

  private fun PyStringElement.formatQuote() = if (isTripleQuoted) TRIPLE_QUOTE else fixQuote(content)

  private fun PyStringElement.formatPrefix() = if (prefixLength <= 1) prefix else prefix.toCharArray().sortedDescending().joinToString("")

  // TODO: use CharArray
  private fun fixedContent(text: String): String {
    val contentLines = text.lines().filter { it.isNotBlank() }
    if (contentLines.size == 1) return text
    if (contentLines.isEmpty()) return ""
    val lines = mutableListOf<String>()
    val initIntent = contentLines.first().takeWhile { it.isWhitespace() }.length / 4
    var prevIndent = initIntent
    contentLines.forEach {
      val stripped = it.trimStart()
      val curIndent = (it.length - stripped.length) / 4

      val indentChange = curIndent - prevIndent
      lines.add(
        if (indentChange == 0) {
          "\n"
        }
        else if (indentChange == 1) {
          scopeInChar.toString()
        }
        else if (indentChange < 0 && (it.length - stripped.length) % 4 == 0) {
          scopeOutChar.toString().repeat(-indentChange)
        }
        else if (indentChange > 0) {
          scopeInChar.toString()
        }
        else {
          scopeOutChar.toString()
        }
      )
      lines.add(stripped)
      prevIndent = curIndent
    }

    val tail = if (prevIndent == initIntent) "\n" else scopeOutChar.toString().repeat(abs(prevIndent - initIntent))

    return lines.joinToString("") + tail
  }

  private fun formatWhitespaces(tab: String, content: String, indentLevel: Int): Pair<String, Int> {
    val curIndent = tab.length / 4
    val indentChange = curIndent - indentLevel

    val text = if (indentChange == 0) {
      "\n"
    }
    else if (indentChange > 0) {
      scopeInChar + content
    }
    else {
      content + scopeOutChar.toString().repeat(-indentChange)
    }
    return text to curIndent
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
