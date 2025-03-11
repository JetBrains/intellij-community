// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.java.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.cce.visitor.LineCompletionEvaluationVisitor
import com.intellij.cce.visitor.LineCompletionVisitorFactory
import com.intellij.cce.visitor.LineCompletionVisitorHelper
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.parentOfType

class JavaLineCompletionVisitorFactory : LineCompletionVisitorFactory {
  override val language: Language = Language.JAVA
  override fun createVisitor(featureName: String, mode: CompletionGolfMode): LineCompletionEvaluationVisitor =
    when(featureName) {
        "text-completion" -> CommentsTokensVisitor(featureName)
        else -> when (mode) {
          CompletionGolfMode.ALL -> throw UnsupportedOperationException("Completion Golf mode \"ALL\" is not supported for Java completion.")
        CompletionGolfMode.TOKENS -> TokensVisitor(featureName)
      }
    }

  class TokensVisitor(override val feature: String) : LineCompletionEvaluationVisitor, JavaRecursiveElementVisitor() {
    private val visitorHelper = LineCompletionVisitorHelper()

    override val language: Language = Language.JAVA

    override fun getFile(): CodeFragment = visitorHelper.getFile()

    override fun visitJavaFile(file: PsiJavaFile) {
      visitorHelper.visitFile(file)
      super.visitJavaFile(file)
    }

    override fun visitVariable(variable: PsiVariable) {
      variable.nameIdentifier?.node?.let { visitorHelper.addElement(it) }
      super.visitVariable(variable)
    }

    override fun visitMethod(method: PsiMethod) {
      method.nameIdentifier?.node?.let { visitorHelper.addElement(it) }
      super.visitMethod(method)
    }

    override fun visitClass(aClass: PsiClass) {
      aClass.nameIdentifier?.node?.let { visitorHelper.addElement(it) }
      super.visitClass(aClass)
    }

    override fun visitKeyword(keyword: PsiKeyword) {
      visitorHelper.addElement(keyword.node)
    }

    override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
      expression.acceptChildren(this)
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
      reference.referenceNameElement?.let { visitorHelper.addElement(it.node) }
      super.visitReferenceElement(reference)
    }

    override fun visitLiteralExpression(expression: PsiLiteralExpression) {
      if (expression is PsiLiteralExpressionImpl &&
          (expression.type == PsiTypes.booleanType() || expression.type == PsiTypes.nullType())) {
        visitorHelper.addElement(expression.node)
      }
    }

    override fun visitPackageStatement(statement: PsiPackageStatement) = Unit
    override fun visitImportStatement(statement: PsiImportStatement) = Unit
    override fun visitImportStaticStatement(statement: PsiImportStaticStatement) = Unit
    override fun visitComment(comment: PsiComment) = Unit
  }

  class CommentsTokensVisitor(override val feature: String) : LineCompletionEvaluationVisitor, JavaRecursiveElementVisitor() {
    private val visitorHelper = LineCompletionVisitorHelper()

    override val language: Language = Language.JAVA

    override fun getFile(): CodeFragment = visitorHelper.getFile()

    override fun visitJavaFile(file: PsiJavaFile) {
      visitorHelper.visitFile(file)
      super.visitJavaFile(file)
    }

    private fun isCommentElement(element: PsiElement): Boolean {
      return element is PsiComment;
    }

    private fun isDocComment(element: PsiElement): Boolean {
      return element.node.elementType == JavaTokenType.C_STYLE_COMMENT ||
             (element is LeafPsiElement && element.parentOfType<PsiDocComment>(withSelf = true) != null)
    }

    override fun visitElement(element: PsiElement) {
      if (isCommentElement(element) || isDocComment(element)) {
        visitorHelper.addElement(element.node)
      }
      super.visitElement(element)
    }
  }
}

