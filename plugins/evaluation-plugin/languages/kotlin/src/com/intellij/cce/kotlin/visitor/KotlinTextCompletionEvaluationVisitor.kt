package com.intellij.cce.kotlin.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinTextCompletionEvaluationVisitor : EvaluationVisitor, KtTreeVisitorVoid() {
  private var codeFragment: CodeFragment? = null
  override val language = Language.KOTLIN
  override val feature = "text-completion"
  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitKtFile(file: KtFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitKtFile(file)
  }

  override fun visitElement(element: PsiElement) {
    if (isCommentElement(element) || isKDocComment(element)) {
      codeFragment?.addChild(CodeToken(element.text, element.textOffset))
    }

    super.visitElement(element)
  }

  private fun isCommentElement(element: PsiElement): Boolean = element is PsiComment

  private fun isKDocComment(element: PsiElement): Boolean = element.elementType == KtTokens.BLOCK_COMMENT
}