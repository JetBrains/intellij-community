// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.chain.AbstractCallChainHintsProvider
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.parameterInfo.HintsTypeRenderer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

/**
 * Kotlin's analog for Java's [com.intellij.codeInsight.hints.MethodChainsInlayProvider]
 *
 * Test - [org.jetbrains.kotlin.idea.codeInsight.hints.KotlinCallChainHintsProviderTest]
 */
class KotlinCallChainHintsProvider : AbstractCallChainHintsProvider<KtQualifiedExpression, KotlinType, BindingContext>() {
    override val previewText: String
        get() = """
            fun main() {
                (1..100).filter { it % 2 == 0 }
                    .map { it * 2 }
                    .takeIf { list ->
                        list.all { it % 2 == 0 }
                    }
                    ?.map { "item: ${'$'}it" }
                    ?.forEach { println(it) }
            }

            inline fun IntRange.filter(predicate: (Int) -> Boolean): List<Int> = TODO()
            inline fun <T, R> Iterable<T>.map(transform: (T) -> R): List<R> = TODO()
            inline fun <T> T.takeIf(predicate: (T) -> Boolean): T? = TODO()
            inline fun <T> Iterable<T>.all(predicate: (T) -> Boolean): Boolean = TODO()
            inline fun <T> Iterable<T>.forEach(action: (T) -> Unit): Unit = TODO()
            inline fun println(message: Any?) = TODO()
        """.trimIndent()

    override fun KotlinType.getInlayPresentation(
        expression: PsiElement,
        factory: PresentationFactory,
        project: Project,
        context: BindingContext
    ): InlayPresentation {
        val inlayInfoDetails = HintsTypeRenderer
            .getInlayHintsTypeRenderer(context, expression as? KtElement ?: error("Only Kotlin psi are possible"))
            .renderType(this)
        return KotlinAbstractHintsProvider.getInlayPresentationForInlayInfoDetails(
            InlayInfoDetails(InlayInfo("", expression.textRange.endOffset), inlayInfoDetails),
            factory,
            project,
            this@KotlinCallChainHintsProvider
        )
    }

    override fun getTypeComputationContext(topmostDotQualifiedExpression: KtQualifiedExpression): BindingContext {
        return topmostDotQualifiedExpression.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL)
    }

    override fun PsiElement.getType(context: BindingContext): KotlinType? {
        return context.getType(this as? KtExpression ?: return null)
    }

    override val dotQualifiedClass: Class<KtQualifiedExpression>
        get() = KtQualifiedExpression::class.java

    override fun KtQualifiedExpression.getReceiver(): PsiElement {
        return receiverExpression
    }

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
}
