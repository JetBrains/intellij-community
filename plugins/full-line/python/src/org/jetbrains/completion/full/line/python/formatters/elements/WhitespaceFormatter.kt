package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.completion.full.line.language.ElementFormatter
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase

class WhitespaceFormatter(private val scopeInChar: Char, private val scopeOutChar: Char) : ElementFormatter {
  private var indentLevel: Pair<PsiFile, Int>? = null

  override fun condition(element: PsiElement): Boolean = element is PsiWhiteSpace

  override fun format(element: PsiElement): String {
    if (element.prevSibling is PsiComment) return ""
    if (element.containingFile != indentLevel?.first) indentLevel = element.containingFile to 0
    if (indentLevel == null) throw RuntimeException("indentLevel can't be null")

    return formatWhitespaces(element.text)
  }

  override fun resetState() {
    indentLevel = indentLevel?.let { it.first to 0 }
  }

  override fun formatFinalElement(element: PsiElement, range: TextRange): String {
    if (indentLevel == null) indentLevel = element.containingFile to 0
    return formatWhitespaces(TextRange(0, range.endOffset - element.textRange.startOffset).substring(element.text))
  }

  fun increaseIntendLevel(file: PsiFile) {
    if (indentLevel == null) {
      indentLevel = file to 1
      return
    }
    val (intentFile, level) = indentLevel!!
    if (intentFile != file) return

    indentLevel = intentFile to level + 1
  }

  fun decreaseIntendLevel(file: PsiFile) {
    if (indentLevel == null) throw RuntimeException("indentLevel can't be null")

    val (intentFile, level) = indentLevel!!
    if (level <= 0) throw RuntimeException("Can't decrease the indentation level that is less than one")
    if (intentFile != file) return

    indentLevel = intentFile to level - 1
  }

  private fun formatWhitespaces(initText: String): String {
    if (!isNewLine(initText)) return initText
    val (intentFile, level) = indentLevel!!

    var text = initText.replace(Regex("\\n\\s*\\n"), "\n")
    val tab = text.takeLastWhile { it != '\n' }
    val curIndent = tab.length / 4
    val indentChange = curIndent - level

    text = if (indentChange == 0) {
      text.dropLast(tab.length)
    }
    else if (indentChange > 0) {
      scopeInChar.toString()
    }
    else {
      scopeOutChar.toString().repeat(-indentChange)
    }
    indentLevel = intentFile to curIndent

    return text
  }

  private fun isNewLine(text: String): Boolean {
    return text.contains("\n")
  }

  companion object {
    fun CodeFormatterBase.increaseIntendLevel(file: PsiFile) {
      val formatter = elementFormatters.first { it is WhitespaceFormatter } as WhitespaceFormatter
      formatter.increaseIntendLevel(file)
    }

    fun CodeFormatterBase.decreaseIntendLevel(file: PsiFile) {
      val formatter = elementFormatters.first { it is WhitespaceFormatter } as WhitespaceFormatter
      formatter.decreaseIntendLevel(file)
    }
  }
}
