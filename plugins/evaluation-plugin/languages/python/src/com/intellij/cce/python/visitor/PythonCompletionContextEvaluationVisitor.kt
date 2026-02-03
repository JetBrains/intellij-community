package com.intellij.cce.python.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeFragmentWithPsi
import com.intellij.cce.core.CodeTokenWithPsi
import com.intellij.cce.core.Language
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyRecursiveElementVisitor

internal class PythonCompletionContextEvaluationVisitor: EvaluationVisitor, PyRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language = Language.PYTHON

  override val feature = "completion-context"

  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitPyFile(node: PyFile) {
    codeFragment = CodeFragmentWithPsi(
      offset = node.textOffset,
      length = node.textLength,
      element = node
    ).apply { text = node.text }
    super.visitPyFile(node)
  }

  override fun visitPyFunction(node: PyFunction) {
    val file = codeFragment
    if (file != null) {
      val start = node.statementList.textRange.startOffset
      val text = node.statementList.text
      val token = CodeTokenWithPsi(
        text = text.toString(),
        offset = start,
        element = node,
        properties = SimpleTokenProperties.create(
          tokenType = TypeProperty.FUNCTION,
          location = SymbolLocation.UNKNOWN,
          init = {}
        )
      )
      file.addChild(token)
    }
    super.visitPyFunction(node)
  }
}
