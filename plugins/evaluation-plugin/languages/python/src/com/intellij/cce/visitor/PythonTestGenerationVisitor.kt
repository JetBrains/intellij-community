package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.roots.TestSourcesFilter
import com.jetbrains.python.psi.PyRecursiveElementVisitor

import com.jetbrains.python.psi.*

class PythonTestGenerationVisitor: EvaluationVisitor, PyRecursiveElementVisitor() {
  private var _codeFragment: CodeFragment? = null

  override val language = Language.PYTHON
  override val feature = "test-generation"

  override fun getFile(): CodeFragment = _codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitPyFile(file: PyFile) {
    _codeFragment = CodeFragment(file.textOffset, file.textLength)

    // Check if the file is in test sources, and skip it if it is
    if (TestSourcesFilter.isTestSources(file.virtualFile, file.project)) {
      return
    }

    super.visitPyFile(file)
  }

  override fun visitPyFunction(function: PyFunction) {
    // Skip private methods
    if (function.name?.startsWith("_") == true){
      return
    }

    // Skip functions without a body
    if (function.statementList.statements.isEmpty()){
      return
    }

    _codeFragment?.addChild(CodeToken(function.text, function.textRange.startOffset, getFunctionProperties()))
  }

  private fun getFunctionProperties(): TokenProperties {
    return SimpleTokenProperties.create(TypeProperty.FUNCTION, SymbolLocation.UNKNOWN) { }
  }
}