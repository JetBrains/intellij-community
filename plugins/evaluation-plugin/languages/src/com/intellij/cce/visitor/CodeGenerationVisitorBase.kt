package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

abstract class CodeGenerationVisitorBase(override val language: Language) : EvaluationVisitor, PsiElementVisitor() {
  private var codeFragment: CodeFragment? = null
  override val feature: String = "code-generation"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")


  override fun visitFile(node: PsiFile) {
    codeFragment = CodeFragment(node.textOffset, node.textLength).also {
      node.accept(createPsiVisitor(it))
    }
  }

  protected abstract fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor
}