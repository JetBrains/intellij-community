package com.intellij.cce.python.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.RenameVisitorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyRecursiveElementVisitor

class PythonRenameVisitor : RenameVisitorBase(Language.PYTHON) {
  override fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor {
    return PythonRenamePsiVisitor(codeFragment)
  }

  private class PythonRenamePsiVisitor(private val codeFragment: CodeFragment) : PyRecursiveElementVisitor() {
    override fun visitElement(element: PsiElement) {
      val parent = element.parent
      when (element.elementType) {
        PyTokenTypes.IDENTIFIER -> {
          if (parent is PyFunction) {
            addToken(element, TypeProperty.METHOD)
          }
        }
      }
      super.visitElement(element)
    }

    override fun visitPyParameter(node: PyParameter) {
      addToken(node, TypeProperty.PARAMETER)
      super.visitPyParameter(node)
    }

    private fun addToken(element: PsiElement?, tokenType: TypeProperty) {
      if (element != null)  {
        codeFragment.addChild(
          CodeToken(element.text, element.startOffset, SimpleTokenProperties.create(tokenType, SymbolLocation.PROJECT) {})
        )
      }
    }
  }
}