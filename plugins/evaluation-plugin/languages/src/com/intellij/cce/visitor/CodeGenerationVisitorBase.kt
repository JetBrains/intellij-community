package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

abstract class EvaluationVisitorBase(override val language: Language, override val feature: String) : EvaluationVisitor, PsiElementVisitor() {
  private var codeFragment: CodeFragment? = null


  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")


  override fun visitFile(psiFile: PsiFile) {
    codeFragment = CodeFragment(psiFile.textOffset, psiFile.textLength).also {
      psiFile.accept(createPsiVisitor(it))
    }
  }

  protected abstract fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor
}

abstract class CodeGenerationVisitorBase(language: Language) : EvaluationVisitorBase(language, "code-generation")

abstract class RenameVisitorBase(language: Language) : EvaluationVisitorBase(language, "rename")