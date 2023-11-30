// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeLine
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.cce.util.CompletionGolfTextUtil.isValuableString
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*


class KotlinCompletionGolfVisitorFactory : CompletionGolfVisitorFactory {
  override val language: Language = Language.KOTLIN
  override fun createVisitor(featureName: String, mode: CompletionGolfMode): CompletionGolfEvaluationVisitor {
    when (mode) {
      CompletionGolfMode.ALL -> return AllVisitor(featureName)
      CompletionGolfMode.TOKENS -> return TokensVisitor(featureName)
    }
  }

  class AllVisitor(override val feature: String) : CompletionGolfAllEvaluationVisitor, KtTreeVisitorVoid() {
    override val language: Language = Language.KOTLIN
    override val processor = CompletionGolfAllEvaluationVisitor.Processor()

    override fun visitComment(comment: PsiComment) {
      processor.skipElement(comment)
      super.visitComment(comment)
    }

    override fun visitKtFile(file: KtFile) {
      processor.visitFile(file)
      super.visitFile(file)
    }

    override fun visitElement(element: PsiElement) {
      if (element.endOffset == element.containingFile.endOffset && element.children.isEmpty()) {
        processor.handleLastElement(element)
      }
      super.visitElement(element)
    }

    override fun visitWhiteSpace(space: PsiWhiteSpace) {
      processor.handleMultilineElement(space)
      super.visitWhiteSpace(space)
    }
  }

  class TokensVisitor(override val feature: String) : CompletionGolfEvaluationVisitor, KtTreeVisitorVoid() {
    private var codeFragment: CodeFragment? = null
    private val lines = mutableListOf<CodeLine>()

    override val language: Language = Language.KOTLIN

    override fun getFile(): CodeFragment {
      codeFragment?.let { file ->
        lines.filter { it.getChildren().isNotEmpty() }.forEach { file.addChild(it) }
        return file
      }
      throw PsiConverterException("Invoke 'accept' with visitor on PSI first")
    }

    override fun visitKtFile(file: KtFile) {
      codeFragment = CodeFragment(file.textOffset, file.textLength)
      lines.clear()
      var offset = 0
      for (line in file.text.lines()) {
        lines.add(CodeLine(line, offset))
        offset += line.length + 1
      }
      super.visitKtFile(file)
    }

    override fun visitElement(element: PsiElement) {
      if (KtTokens.KEYWORDS.contains(element.elementType) || KtTokens.SOFT_KEYWORDS.contains(element.elementType)) {
        addElement(element.node)
      }
      super.visitElement(element)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
      if (expression !is KtOperationReferenceExpression) {
        addElement(expression.node)
      }
    }

    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
      declaration.nameIdentifier?.node?.let { addElement(it) }
      super.visitNamedDeclaration(declaration)
    }

    override fun visitImportList(importList: KtImportList) = Unit

    override fun visitPackageDirective(directive: KtPackageDirective) = Unit

    private fun addElement(element: ASTNode) {
      val text = element.text.take(MAX_PREFIX_LENGTH)
      if (text.isValuableString()) {
        lines.find { it.offset <= element.startOffset && it.offset + it.text.length > element.startOffset }?.addChild(
          CodeToken(text, element.startOffset)
        )
      }
    }

    companion object {
      const val MAX_PREFIX_LENGTH: Int = 4
    }
  }
}
