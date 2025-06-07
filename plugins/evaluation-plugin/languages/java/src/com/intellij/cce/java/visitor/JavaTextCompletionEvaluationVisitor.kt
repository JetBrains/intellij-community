package com.intellij.cce.java.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*

class JavaTextCompletionEvaluationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null
  override val language = Language.JAVA
  override val feature = "text-completion"
  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitJavaFile(file)
  }

  override fun visitElement(element: PsiElement) {
    if (isCommentElement(element) || isDocComment(element)) {
      codeFragment?.addChild(CodeToken(element.text, element.textOffset))
    }
    super.visitElement(element)
  }

  private fun isCommentElement(element: PsiElement): Boolean = element is PsiComment

  private fun isDocComment(element: PsiElement): Boolean = element.node.elementType == JavaTokenType.C_STYLE_COMMENT
}