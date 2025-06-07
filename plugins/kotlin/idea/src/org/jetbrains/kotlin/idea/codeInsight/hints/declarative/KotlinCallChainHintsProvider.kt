// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import com.intellij.codeInsight.hints.chain.AbstractDeclarativeCallChainProvider
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.AbstractKotlinInlayHintsProvider.Companion.addInlayInfoDetail
import org.jetbrains.kotlin.idea.parameterInfo.HintsTypeRenderer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError

class KotlinCallChainHintsProvider: AbstractDeclarativeCallChainProvider<KtQualifiedExpression, KotlinType, BindingContext>() {
    override fun KotlinType.buildTree(
        expression: PsiElement,
        project: Project,
        context: BindingContext,
        treeBuilder: PresentationTreeBuilder
    ) {
        val inlayInfoDetails: List<InlayInfoDetail> = HintsTypeRenderer
            .getInlayHintsTypeRenderer(context, expression as? KtElement ?: error("Only Kotlin psi are possible"))
            .renderTypeIntoInlayInfo(this)

        inlayInfoDetails.forEach {
            treeBuilder.addInlayInfoDetail(it)
        }
    }

    override fun PsiElement.getType(context: BindingContext): KotlinType? {
        return context.getType(this as? KtExpression ?: return null)?.takeUnless { it.isError }
    }

    override val dotQualifiedClass: Class<KtQualifiedExpression> = KtQualifiedExpression::class.java

    override fun KtQualifiedExpression.getReceiver(): PsiElement =
        receiverExpression

    override fun KtQualifiedExpression.getParentDotQualifiedExpression(): KtQualifiedExpression? {
        var expr: PsiElement? = parent
        while (
            expr is KtPostfixExpression ||
            expr is KtParenthesizedExpression ||
            expr is KtArrayAccessExpression ||
            expr is KtCallExpression
        ) {
            expr = expr.parent
        }
        return expr as? KtQualifiedExpression
    }

    override fun PsiElement.skipParenthesesAndPostfixOperatorsDown(): PsiElement? {
        var expr: PsiElement? = this
        while (true) {
            expr = when (expr) {
                is KtPostfixExpression -> expr.baseExpression
                is KtParenthesizedExpression -> expr.expression
                is KtArrayAccessExpression -> expr.arrayExpression
                is KtCallExpression -> expr.calleeExpression
                else -> break
            }
        }
        return expr
    }

    override fun getTypeComputationContext(topmostDotQualifiedExpression: KtQualifiedExpression): BindingContext =
        topmostDotQualifiedExpression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_NO_ADDITIONAL)

}