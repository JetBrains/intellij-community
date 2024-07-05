package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.jetbrains.python.psi.*

class PythonMultiLineEvaluationVisitor : EvaluationVisitor, PyRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null
  override val language = Language.PYTHON
  override val feature = "multi-line-completion"
  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitPyFile(node: PyFile) {
    codeFragment = CodeFragment(node.textOffset, node.textLength).apply { text = node.text }
    super.visitPyFile(node)
  }

  override fun visitElement(element: PsiElement) {
    if (element is PyStatementList) { visitPyStatementList(element) }
    if (element is PyFunction) visitPyFunction(element)
    super.visitElement(element)
  }

  override fun visitPyClass(node: PyClass) {
    node.methods.forEach { visitPyFunction(it) }
  }

  override fun visitPyFunction(node: PyFunction) {
    visitPyStatementList(node.statementList)
  }

  override fun visitPyStatementList(node: PyStatementList) {
    // predicting properties and entire methods with declarations can be nice,
    // but harder to pick good positions as IDE could fill some of the class attributes automatically,
    // so there is little value in benchmarking it
    if (node.parent is PyClass) return

    codeFragment?.let { file ->
      val blocks = node.splitByIndents()
      blocks.forEach { file.addChild(it) }
    }
  }

  private fun PsiElement.lineRanges(document: Document): List<Pair<TextRange, Int>> = buildList {
    val offset = startOffset
    var pos = 0
    val text = text
    while (pos < text.length) {
      val nextLineBreakPos = text.indexOf('\n', pos)
      if (nextLineBreakPos == -1) break
      val range = TextRange(pos + offset, nextLineBreakPos + offset)
      add(range to document.getText(range).indent)
      pos = nextLineBreakPos + 1
    }
    val lastRange = TextRange(pos + offset, textLength + offset)
    add(lastRange to document.getText(lastRange).indent)
  }

  private fun <T> List<T>.indexOfFirst(start: Int, predicate: (T) -> Boolean): Int {
    var index = start
    for (item in subList(index, this.size)) {
      if (predicate(item))
        return index
      index++
    }
    return -1
  }

  private fun containsValuableSymbols(line: String) = line.any(::isValuableCharacter)
  private fun isValuableCharacter(c: Char) = c.isLetterOrDigit() || valuableCharacters.contains(c)
  private val valuableCharacters = arrayOf('+', '-', '*', '%', '=', '&', '|', '@', '$', '?', '_')

  private fun PsiElement.splitByIndents(): List<CodeToken> = buildList {
    val document = PsiDocumentManager.getInstance(project).getDocument(containingFile) ?: return emptyList()
    val lineRanges = lineRanges(document)
    val end: Pair<TextRange, Int> = TextRange(endOffset, endOffset) to 0
    for (i in lineRanges.indices) {
      val (range, indent) = lineRanges[i]
      val line = document.getText(range)
      if (line.isBlank() || !containsValuableSymbols(line) || line.dropWhile { it.isWhitespace() }.startsWith("#")){
        continue
      }

      val lastInScope = lineRanges
        .asSequence()
        .drop(i)
        .takeWhile { it.second >= indent }
        .lastOrNull() ?: end

      val scopeRange = TextRange(range.startOffset, lastInScope.first.endOffset)
      val scopeText = document.getText(scopeRange)
      add(CodeToken(scopeText, range.startOffset))
    }
  }

  private val String.indent: Int
    get() = takeWhile { it.isWhitespace() }.count()
}
