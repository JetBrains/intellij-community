package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.startOffset
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyStatementList

class PythonCodeGenerationVisitor : CodeGenerationVisitorBase(Language.PYTHON) {
  override fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor {
    return PythonCodeGenerationPsiVisitor(codeFragment)
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