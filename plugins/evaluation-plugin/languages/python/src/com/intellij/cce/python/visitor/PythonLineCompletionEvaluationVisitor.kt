// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.python.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.cce.visitor.LineCompletionAllEvaluationVisitor
import com.intellij.cce.visitor.LineCompletionEvaluationVisitor
import com.intellij.cce.visitor.LineCompletionVisitorFactory
import com.intellij.cce.visitor.LineCompletionVisitorHelper
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.jetbrains.python.PythonTokenSetContributor
import com.jetbrains.python.psi.*

class PythonLineCompletionVisitorFactory : LineCompletionVisitorFactory {
  override val language: Language = Language.PYTHON
  override fun createVisitor(featureName: String, mode: CompletionGolfMode): LineCompletionEvaluationVisitor =
    when (mode) {
      CompletionGolfMode.ALL -> AllVisitor(featureName)
      CompletionGolfMode.TOKENS -> TokensVisitor(featureName)
    }

  class AllVisitor(override val feature: String) : LineCompletionAllEvaluationVisitor, PyRecursiveElementVisitor() {
    override val language: Language = Language.PYTHON
    override val processor = LineCompletionAllEvaluationVisitor.Processor()

    override fun visitComment(comment: PsiComment) {
      processor.skipElement(comment)
      super.visitComment(comment)
    }

    override fun visitPyFile(file: PyFile) {
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

    override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
      if (node.isDocString) processor.skipElement(node)
      super.visitPyStringLiteralExpression(node)
    }
  }

  class TokensVisitor(override val feature: String) : LineCompletionEvaluationVisitor, PyRecursiveElementVisitor() {
    private val visitorHelper = LineCompletionVisitorHelper()
    private val tokenSetContributor = PythonTokenSetContributor()

    override val language: Language = Language.PYTHON

    override fun getFile(): CodeFragment = visitorHelper.getFile()

    override fun visitPyFile(file: PyFile) {
      visitorHelper.visitFile(file)
      super.visitFile(file)
    }

    override fun visitElement(element: PsiElement) {
      if (tokenSetContributor.keywordTokens.contains(element.elementType)) {
        visitorHelper.addElement(element.node)
      }
      else if (element is PsiNameIdentifierOwner) {
        element.nameIdentifier?.node?.let { visitorHelper.addElement(it) }
      }
      super.visitElement(element)
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      node.nameElement?.let { visitorHelper.addElement(it) }
      super.visitPyReferenceExpression(node)
    }

    override fun visitPyKeywordArgument(node: PyKeywordArgument) {
      node.keywordNode?.let { visitorHelper.addElement(it) }
      super.visitPyKeywordArgument(node)
    }

    override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
      if (node.parent is PySubscriptionExpression) {
        visitorHelper.addElement(node.node)
      }
      else {
        super.visitPyStringLiteralExpression(node)
      }
    }
  }
}
