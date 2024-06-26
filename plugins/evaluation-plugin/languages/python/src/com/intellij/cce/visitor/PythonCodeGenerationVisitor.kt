package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.startOffset
import com.jetbrains.python.psi.*

class PythonCodeGenerationVisitor : EvaluationVisitor, PsiElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language = Language.PYTHON
  override val feature: String = "code-generation"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitFile(node: PsiFile) {
    codeFragment = CodeFragment(node.textOffset, node.textLength).also {
      PythonCodeGenerationPsiVisitor(it).visitFile(node)
    }
  }
}

private class PythonCodeGenerationPsiVisitor(private val codeFragment: CodeFragment) : PyRecursiveElementVisitor() {
  override fun visitPyFunction(node: PyFunction) {
    codeFragment.addChild(
      CodeToken(node.text, node.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {})
    )
    super.visitPyFunction(node)
  }

  override fun visitElement(element: PsiElement) {
    if (element is PyStatementList) {
      codeFragment.addChild(
        CodeToken(element.text, element.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD_BODY, SymbolLocation.PROJECT) {})
      )
    }

    super.visitElement(element)
  }
}