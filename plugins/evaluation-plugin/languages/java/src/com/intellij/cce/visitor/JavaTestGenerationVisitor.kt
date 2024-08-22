package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import java.nio.file.Paths


class JavaTestGenerationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  override val feature: String = "test-generation"
  override val language: Language = Language.JAVA
  private var codeFragment: CodeFragment? = null

  override fun getFile(): CodeFragment = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)

    super.visitJavaFile(file)
  }

  override fun visitMethod(method: PsiMethod) {
    codeFragment?.addChild(CodeToken(method.text, method.textRange.startOffset, getMethodProperties()))
  }

  private fun getMethodProperties(): TokenProperties{
    return SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.UNKNOWN) { }
  }
}
