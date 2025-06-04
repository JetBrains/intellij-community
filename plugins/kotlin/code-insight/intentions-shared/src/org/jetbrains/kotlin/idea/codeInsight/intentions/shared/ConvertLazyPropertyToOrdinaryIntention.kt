// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.allChildren

class ConvertLazyPropertyToOrdinaryIntention : SelfTargetingIntention<KtProperty>(
  KtProperty::class.java, KotlinBundle.lazyMessage("convert.to.ordinary.property")
) {
    override fun isApplicableTo(element: KtProperty, caretOffset: Int): Boolean {
        val delegateExpression = element.delegate?.expression as? KtCallExpression ?: return false
        val lambdaBody = delegateExpression.functionLiteral()?.bodyExpression ?: return false
        if (lambdaBody.statements.isEmpty()) return false
        return delegateExpression.isCalling(FqName("kotlin.lazy"))
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        val delegate = element.delegate ?: return
        val delegateExpression = delegate.expression as? KtCallExpression ?: return
        val functionLiteral = delegateExpression.functionLiteral() ?: return
        element.initializer = functionLiteral.singleStatement()
                              ?: KtPsiFactory(element.project).createExpression("run ${functionLiteral.text}")
        delegate.delete()
    }

    private fun KtCallExpression.functionLiteral(): KtFunctionLiteral? {
        return lambdaArguments.singleOrNull()?.getLambdaExpression()?.functionLiteral
    }

    private fun KtFunctionLiteral.singleStatement(): KtExpression? {
        val body = this.bodyExpression ?: return null
        if (body.allChildren.any { it is PsiComment }) return null
        return body.statements.singleOrNull()
    }
}