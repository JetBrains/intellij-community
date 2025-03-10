// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.kotlin.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.cce.visitor.LineCompletionAllEvaluationVisitor
import com.intellij.cce.visitor.LineCompletionEvaluationVisitor
import com.intellij.cce.visitor.LineCompletionVisitorFactory
import com.intellij.cce.visitor.LineCompletionVisitorHelper
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*


class KotlinLineCompletionVisitorFactory : LineCompletionVisitorFactory {
  override val language: Language = Language.KOTLIN
  override fun createVisitor(featureName: String, mode: CompletionGolfMode): LineCompletionEvaluationVisitor {
    when (mode) {
      CompletionGolfMode.ALL -> return AllVisitor(featureName)
      CompletionGolfMode.TOKENS -> return TokensVisitor(featureName)
    }
  }

  class AllVisitor(override val feature: String) : LineCompletionAllEvaluationVisitor, KtTreeVisitorVoid() {
    override val language: Language = Language.KOTLIN
    override val processor = LineCompletionAllEvaluationVisitor.Processor()

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

  class TokensVisitor(override val feature: String) : LineCompletionEvaluationVisitor, KtTreeVisitorVoid() {
    private val visitorHelper = LineCompletionVisitorHelper()

    override val language: Language = Language.KOTLIN

    override fun getFile(): CodeFragment = visitorHelper.getFile()

    override fun visitKtFile(file: KtFile) {
      visitorHelper.visitFile(file)
      super.visitKtFile(file)
    }

    override fun visitElement(element: PsiElement) {
      if (KtTokens.KEYWORDS.contains(element.elementType) || KtTokens.SOFT_KEYWORDS.contains(element.elementType)) {
        visitorHelper.addElement(element.node)
      }
      super.visitElement(element)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
      if (expression !is KtOperationReferenceExpression) {
        visitorHelper.addElement(expression.node)
      }
    }

    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
      declaration.nameIdentifier?.node?.let { visitorHelper.addElement(it) }
      super.visitNamedDeclaration(declaration)
    }

    override fun visitImportList(importList: KtImportList) = Unit

    override fun visitPackageDirective(directive: KtPackageDirective) = Unit
  }
}
