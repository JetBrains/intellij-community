package com.intellij.cce.python.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.visitor.CodeGenerationVisitorBase
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.startOffset
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyRecursiveElementVisitor

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

    val body = node.statementList
    codeFragment.addChild(
      CodeToken(body.text, body.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD_BODY, SymbolLocation.PROJECT) {})
    )
    super.visitPyFunction(node)
  }
}