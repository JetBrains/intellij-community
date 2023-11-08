package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
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

  override fun visitPyFunction(node: PyFunction) {
    codeFragment?.let { file ->
      val start = node.statementList.textRange.startOffset
      val text = node.statementList.text
      file.addChild(CodeToken(text.toString(), start, METHOD_PROPERTIES))
    }
    super.visitPyFunction(node)
  }
}

private val METHOD_PROPERTIES = SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.UNKNOWN) {}
