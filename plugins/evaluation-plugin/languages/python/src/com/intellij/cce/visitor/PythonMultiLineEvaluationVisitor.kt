package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.python.psi.*

class PythonMultiLineEvaluationVisitor : EvaluationVisitor, PyRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null
  override val language = Language.PYTHON
  override val feature = "multi-line-completion"
  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitPyFile(node: PyFile) {
    codeFragment = CodeFragment(node.textOffset, node.textLength)
      .apply { text = node.text }
      .also { visitNonEmptyLines(node, it) }
    super.visitPyFile(node)
  }

  private fun visitNonEmptyLines(node: PyFile, file: CodeFragment) {
    val document = node.fileDocument
    val endOffset = node.fileDocument.textLength
    for (line in 0 until document.lineCount) {
      val lineStartOffset = document.getLineStartOffset(line).let {
        var pos = it
        while (pos < endOffset && document.text[pos].isWhitespace()) pos ++
        pos
      }
      val lineEndOffset = document.getLineEndOffset(line)
      if (lineStartOffset >= lineEndOffset) continue
      val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))
      if (lineText.isBlank()) continue

      file.addChild(CodeToken(lineText, lineStartOffset, LINE_START))
    }
  }

  override fun visitPyFunction(node: PyFunction) {
    codeFragment?.let { file ->
      val start = node.statementList.textRange.startOffset
      val text = node.statementList.text
      file.addChild(CodeToken(text.toString(), start, FUNCTION))
    }
    super.visitPyFunction(node)
  }

  override fun visitPyClass(node: PyClass) {
    codeFragment?.let { file ->
      val start = node.statementList.startOffset
      val text = file.text.substring(start, node.endOffset)
      file.addChild(CodeToken(text, start, CLASS))
    }
    super.visitPyClass(node)
  }
}

private val FUNCTION = SimpleTokenProperties.create(TypeProperty.FUNCTION, SymbolLocation.UNKNOWN) {}
private val CLASS = SimpleTokenProperties.create(TypeProperty.CLASS, SymbolLocation.UNKNOWN) {}
private val LINE_START = SimpleTokenProperties.create(TypeProperty.LINE, SymbolLocation.UNKNOWN) {
  this["position"] = CaretPosition.BEGINNING.name
}
