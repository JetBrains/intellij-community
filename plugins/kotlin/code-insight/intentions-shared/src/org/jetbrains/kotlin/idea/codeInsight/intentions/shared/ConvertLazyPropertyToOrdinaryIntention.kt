// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.isCallingAnyOf
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren

internal class ConvertLazyPropertyToOrdinaryIntention :
    KotlinApplicableModCommandAction<KtProperty, Unit>(KtProperty::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.ordinary.property")

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        val delegateExpression = element.delegateExpression() ?: return false
        val lambdaBody = delegateExpression.functionLiteral()?.bodyExpression ?: return false
        return !lambdaBody.statements.isEmpty()
    }

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        val delegateExpression = element.delegateExpression() ?: return null
        if (!delegateExpression.isCallingAnyOf(StandardKotlinNames.lazy)) return null
        return Unit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val delegate = element.delegate ?: return
        val delegateExpression = element.delegateExpression() ?: return
        val functionLiteral = delegateExpression.functionLiteral() ?: return
        element.initializer = functionLiteral.singleStatement()
            ?: KtPsiFactory(element.project).createExpression("run ${functionLiteral.text}")
        delegate.delete()
    }
}

private fun KtProperty.delegateExpression(): KtCallExpression? = this.delegate?.expression as? KtCallExpression

private fun KtCallExpression.functionLiteral(): KtFunctionLiteral? {
    return lambdaArguments.singleOrNull()?.getLambdaExpression()?.functionLiteral
}

private fun KtFunctionLiteral.singleStatement(): KtExpression? {
    val body = this.bodyExpression ?: return null
    if (body.allChildren.any { it is PsiComment }) return null
    return body.statements.singleOrNull()
}