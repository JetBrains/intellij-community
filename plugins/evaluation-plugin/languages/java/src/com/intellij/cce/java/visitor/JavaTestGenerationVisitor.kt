package com.intellij.cce.java.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier


class JavaTestGenerationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  override val feature: String = "test-generation"
  override val language: Language = Language.JAVA
  private var codeFragment: CodeFragment? = null

  override fun getFile(): CodeFragment = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)

    if (TestSourcesFilter.isTestSources(file.virtualFile, file.project)) {
      return
    }
    super.visitJavaFile(file)
  }

  override fun visitMethod(method: PsiMethod) {
    if (!method.hasModifierProperty(PsiModifier.PUBLIC) || method.body == null || method.isConstructor) {
      return
    }
    codeFragment?.addChild(CodeToken(method.text, method.textRange.startOffset, getMethodProperties()))
  }

  private fun getMethodProperties(): TokenProperties{
    return SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.UNKNOWN) { }
  }
}