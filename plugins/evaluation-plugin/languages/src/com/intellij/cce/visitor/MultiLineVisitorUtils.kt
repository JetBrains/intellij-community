package com.intellij.cce.visitor

import com.intellij.cce.core.CodeToken
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset

object MultiLineVisitorUtils {
  interface LanguageSupporter {
    val singleLineCommentPrefix: List<String>
    val multiLineCommentPrefix: List<Pair<String, String>>

    companion object {
      val DEFAULT = object : LanguageSupporter {
        override val singleLineCommentPrefix: List<String> = listOf("//")
        override val multiLineCommentPrefix: List<Pair<String, String>> = listOf(Pair("/*", "*/"))
      }
    }
  }

  private fun findEndOfDocstring(start: Int, lines: List<LineInfo>, token: String): Int {
    val end = lines.asSequence().drop(start).indexOfFirst { it.text.startsWith(token) }
    return end.takeIf { it > 0 } ?: lines.size
  }

  private fun LanguageSupporter.getCommentRanges(lines: List<LineInfo>): List<Pair<Int, Int>> {
    val singleLineComments = singleLineCommentPrefix.map { prefix ->
      lines
        .withIndex()
        .filter { (_, line) -> line.text.trimStart().startsWith(prefix) }
        .map { (i, _) -> i to i }
    }

    val multiLineComments = multiLineCommentPrefix.map { (start, end) ->
      buildList {
        var pos = 0
        outer@ while (pos < lines.size) {
          val line = lines[pos].text.trimStart()
          if (line.startsWith(start)) {
            val match = findEndOfDocstring(pos, lines, end)
            add(pos to match)
            pos = match + end.length + 1
            continue@outer
          }
          pos++
        }
      }
    }

    return (singleLineComments + multiLineComments).flatten().sortedBy { it.first }
  }

  data class LineInfo(val text: String, val range: TextRange) {
    val indent: Int = text.indent
    val startOffset: Int = range.startOffset
  }

  private fun Document.getLineInfo(range: TextRange): LineInfo = LineInfo(getText(range), range)
  private fun List<Pair<Int, Int>>.has(offset: Int): Boolean = any { it.first <= offset && offset <= it.second }

  private fun PsiElement.rangeFromLineBeginning(document: Document): TextRange {
    val lineStart = document.getLineStartOffset(document.getLineNumber(startOffset))
    val lineEnd = document.getLineEndOffset(document.getLineNumber(endOffset))
    return TextRange.create(lineStart, lineEnd)
  }

  private fun PsiElement.lineRanges(document: Document): List<LineInfo> = buildList {
    val offset = startOffset
    var pos = 0
    val wholeRange = rangeFromLineBeginning(document)
    val wholeText = document.getText(wholeRange)
    val elementPrefix = wholeText.substring(0, offset - wholeRange.startOffset)
    val elementText = if (elementPrefix.isNotBlank()) { text } else { wholeText }

    while (pos < elementText.length) {
      val nextLineBreakPos = elementText.indexOf('\n', pos)
      if (nextLineBreakPos == -1) break
      val range = TextRange(pos + offset, nextLineBreakPos + offset)
      add(document.getLineInfo(range))
      pos = nextLineBreakPos + 1
    }
    val lastRange = TextRange(pos + offset, textLength + offset)
    add(document.getLineInfo(lastRange))
  }

  fun splitElementByIndents(
    element: PsiElement,
    supporter: LanguageSupporter = LanguageSupporter.DEFAULT,
  ): List<CodeToken> = buildList {
    val document = PsiDocumentManager
      .getInstance(element.project)
      .getDocument(element.containingFile) ?: return emptyList()

    val lineRanges = element.lineRanges(document)
    val commentRanges = supporter.getCommentRanges(lineRanges)
    for (i in lineRanges.indices) {
      if (commentRanges.has(i)) continue

      val lineInfo = lineRanges[i]
      val indent = lineInfo.indent
      if (lineInfo.text.isBlank() || !containsValuableSymbols(lineInfo.text)) continue

      val lastInScopeOffset = lineRanges.asSequence()
        .drop(i)
        .takeWhile { it.indent >= indent }
        .lastOrNull()?.range?.endOffset ?: element.endOffset

      val scopeRange = TextRange(lineInfo.startOffset, lastInScopeOffset)
      val scopeText = document.getText(scopeRange)
      add(CodeToken(scopeText, scopeRange.startOffset))
    }
  }

  private val String.indent: Int
    get() = takeWhile { it.isWhitespace() }.count()

  private fun containsValuableSymbols(line: String) = line.any(::isValuableCharacter)
  private fun isValuableCharacter(c: Char) = c.isLetterOrDigit() || valuableCharacters.contains(c)
  private val valuableCharacters = arrayOf('+', '-', '*', '%', '=', '&', '|', '@', '$', '?', '_')
}