package com.intellij.cce.kotlin.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.psi.*


class KotlinDocGenerationVisitor : EvaluationVisitor, KtTreeVisitorVoid() {
  override val feature: String = "doc-generation"
  override val language: Language = Language.KOTLIN
  private var codeFragment: CodeFragment? = null

  override fun getFile(): CodeFragment = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitKtFile(file: KtFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitKtFile(file)
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    val docComment = function.docComment
    val nameIdentifier = function.nameIdentifier
    if (docComment != null && nameIdentifier != null) {
      codeFragment?.addChild(
        CodeToken(
          function.text,
          function.textRange.startOffset,
          DocumentationProperties(docComment.text, function.startOffset, function.endOffset, docComment.startOffset, docComment.endOffset, nameIdentifier.startOffset)
        )
      )
    }
    super.visitNamedFunction(function)
  }


  override fun visitClass(klass: KtClass) {
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

  override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
    val docComment = declaration.docComment
    val nameIdentifier = declaration.nameIdentifier
    if (docComment != null && nameIdentifier != null) {
      codeFragment?.addChild(
        CodeToken(
          declaration.text,
          declaration.textRange.startOffset,
          DocumentationProperties(docComment.text, declaration.startOffset, declaration.endOffset, docComment.startOffset, docComment.endOffset, nameIdentifier.startOffset)
        )
      )
    }
    super.visitObjectDeclaration(declaration)
  }
}
