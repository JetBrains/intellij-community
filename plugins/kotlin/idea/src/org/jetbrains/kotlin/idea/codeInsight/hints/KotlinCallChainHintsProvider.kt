// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.chain.AbstractCallChainHintsProvider
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.parameterInfo.HintsTypeRenderer
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError

/**
 * Kotlin's analog for Java's [com.intellij.codeInsight.hints.MethodChainsInlayProvider]
 *
 * Test - [org.jetbrains.kotlin.idea.codeInsight.hints.KotlinCallChainHintsProviderTest]
 */
class KotlinCallChainHintsProvider : AbstractCallChainHintsProvider<KtQualifiedExpression, KotlinType, BindingContext>() {

    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP

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
        """.trimIndent()

    override val description: String
        get() = KotlinBundle.message("inlay.kotlin.call.chains.hints")

    override fun createFile(project: Project, fileType: FileType, document: Document): PsiFile =
        KotlinAbstractHintsProvider.createKtFile(project, document, fileType)

    override fun KotlinType.getInlayPresentation(
        expression: PsiElement,
        factory: PresentationFactory,
        project: Project,
        context: BindingContext
    ): InlayPresentation {
        val inlayInfoDetails = HintsTypeRenderer
            .getInlayHintsTypeRenderer(context, expression as? KtElement ?: error("Only Kotlin psi are possible"))
            .renderTypeIntoInlayInfo(this)
        return KotlinAbstractHintsProvider.getInlayPresentationForInlayInfoDetails(
            InlayInfoDetails(InlayInfo("", expression.textRange.endOffset), inlayInfoDetails),
            factory,
            project,
            this@KotlinCallChainHintsProvider
        )
    }

    override fun getTypeComputationContext(topmostDotQualifiedExpression: KtQualifiedExpression): BindingContext {
        return topmostDotQualifiedExpression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_NO_ADDITIONAL)
    }

    override fun PsiElement.getType(context: BindingContext): KotlinType? {
        return context.getType(this as? KtExpression ?: return null)?.takeUnless { it.isError }
    }

    override val dotQualifiedClass: Class<KtQualifiedExpression>
        get() = KtQualifiedExpression::class.java

    override fun KtQualifiedExpression.getReceiver(): PsiElement {
        return receiverExpression
    }

    override val key: SettingsKey<Settings> = SettingsKey("kotlin.call.chains.hints")

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
