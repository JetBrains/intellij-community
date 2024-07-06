package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.PsiElement
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
      val blocks = MultiLineVisitorUtils.splitElementByIndents(node, PythonSupporter)
      blocks.forEach { file.addChild(it) }
    }
  }

  private object PythonSupporter : MultiLineVisitorUtils.LanguageSupporter {
    override fun getCommentRanges(lines: List<MultiLineVisitorUtils.LineInfo>): List<Pair<Int, Int>> {
      val singleLines = lines
        .withIndex()
        .filter { (_, line) -> line.text.trimStart().startsWith("#") }
        .map { (i, _) -> i to i }
      val docstrings = buildList {
        var pos = 0
        outer@ while (pos < lines.size) {
          val line = lines[pos].text.trimStart()
          for (token in DOCSTRING_MARKERS) {
            if (line.startsWith(token)) {
              val match = findEndOfDocstring(pos, lines, token)
              add(pos to match)
              pos = match + 1
              continue@outer
            }
          }
          pos++
        }
      }

      return singleLines + docstrings
    }

    private fun findEndOfDocstring(start: Int, lines: List<MultiLineVisitorUtils.LineInfo>, token: String): Int {
      val end = lines.asSequence().drop(start).indexOfFirst { it.text.startsWith(token) }
      return end.takeIf { it > 0 } ?: lines.size
    }

    private val DOCSTRING_MARKERS = listOf("\"\"\"", "\'\'\'")
  }
}
