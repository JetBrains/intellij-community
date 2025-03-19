package com.intellij.cce.java.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset


class JavaDocGenerationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  override val feature: String = "doc-generation"
  override val language: Language = Language.JAVA
  private var codeFragment: CodeFragment? = null

  override fun getFile(): CodeFragment = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitJavaFile(file)
  }

  override fun visitMethod(method: PsiMethod) {
    val docComment = method.docComment
    val nameIdentifier = method.nameIdentifier
    if (docComment != null && nameIdentifier != null) {
      codeFragment?.addChild(
        CodeToken(
          method.text,
          method.textRange.startOffset,
          DocumentationProperties(docComment.text, method.startOffset, method.endOffset, docComment.startOffset, docComment.endOffset, nameIdentifier.startOffset)
        )
      )
    }
    super.visitMethod(method)
  }


  override fun visitClass(klass: PsiClass) {
    val docComment = klass.docComment
    val nameIdentifier = klass.nameIdentifier
    if (docComment != null && nameIdentifier != null) {
      codeFragment?.addChild(
        CodeToken(
          klass.text,
          klass.textRange.startOffset,
          DocumentationProperties(docComment.text, klass.startOffset, klass.endOffset, docComment.startOffset, docComment.endOffset, nameIdentifier.startOffset)
        )
      )
    }
    super.visitClass(klass)
  }

}
