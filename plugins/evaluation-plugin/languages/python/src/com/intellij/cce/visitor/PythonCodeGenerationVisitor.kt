package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.util.startOffset
import com.jetbrains.python.psi.*

class PythonCodeGenerationVisitor : EvaluationVisitor, PyRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language = Language.PYTHON
  override val feature: String = "code-generation"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")
  override fun visitPyFile(node: PyFile) {
    codeFragment = CodeFragment(node.textOffset, node.textLength)
    super.visitPyFile(node)
  }

  override fun visitPyFunction(node: PyFunction) {
    codeFragment?.addChild(
      CodeToken(node.text, node.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {})
    )
    super.visitPyFunction(node)
  }
}
