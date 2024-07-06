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
    fun getCommentRanges(lines: List<LineInfo>): List<Pair<Int, Int>> = emptyList()
  }

  data class LineInfo(val text: String, val range: TextRange) {
    val indent: Int = text.indent
    val startOffset: Int = range.startOffset
  }

  private fun Document.getLineInfo(range: TextRange): LineInfo = LineInfo(getText(range), range)
  private fun List<Pair<Int, Int>>.has(offset: Int): Boolean = any { it.first <= offset && offset <= it.second }

  private fun PsiElement.lineRanges(document: Document): List<LineInfo> = buildList {
    val offset = startOffset
    var pos = 0
    val text = text
    while (pos < text.length) {
      val nextLineBreakPos = text.indexOf('\n', pos)
      if (nextLineBreakPos == -1) break
      val range = TextRange(pos + offset, nextLineBreakPos + offset)
      add(document.getLineInfo(range))
      pos = nextLineBreakPos + 1
    }
    val lastRange = TextRange(pos + offset, textLength + offset)
    add(document.getLineInfo(lastRange))
  }

  fun splitElementByIndents(element: PsiElement, supporter: LanguageSupporter): List<CodeToken> = buildList {
    val document = PsiDocumentManager
      .getInstance(element.project)
      .getDocument(element.containingFile) ?: return emptyList()

    val lineRanges = element.lineRanges(document)
    val end = TextRange(element.endOffset, element.endOffset)
    val commentRanges: List<Pair<Int, Int>> = supporter.getCommentRanges(lineRanges)
    for (i in lineRanges.indices) {
      if (commentRanges.has(i)) continue

      val lineInfo = lineRanges[i]
      val indent = lineInfo.indent
      if (lineInfo.text.isBlank() || !containsValuableSymbols(lineInfo.text)) continue

      val lastInScope = lineRanges.asSequence()
        .drop(i)
        .takeWhile { it.indent >= indent }
        .lastOrNull()
        ?.range ?: end

      val scopeRange = TextRange(lineInfo.startOffset, lastInScope.endOffset)
      val scopeText = document.getText(scopeRange)
      add(CodeToken(scopeText, lineInfo.startOffset))
    }
  }

  private val String.indent: Int
    get() = takeWhile { it.isWhitespace() }.count()

  private fun containsValuableSymbols(line: String) = line.any(::isValuableCharacter)
  private fun isValuableCharacter(c: Char) = c.isLetterOrDigit() || valuableCharacters.contains(c)
  private val valuableCharacters = arrayOf('+', '-', '*', '%', '=', '&', '|', '@', '$', '?', '_')
}