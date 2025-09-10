// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.chain.AbstractCallChainHintsProvider
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.parameterInfo.HintsTypeRenderer
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
@Deprecated("Use org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinCallChainHintsProvider instead")
open class KotlinCallChainHintsProvider : AbstractCallChainHintsProvider<KtQualifiedExpression, KotlinType, BindingContext>() {

    override val group: InlayGroup
        get() = InlayGroup.METHOD_CHAINS_GROUP

    override val previewText: String
        get() = """
            fun doSomething(list: List<Int>) {
                list.filter { it % 2 == 0 }
                    .map { it * 2 }
                    .takeIf { list ->
                        list.all { it % 2 == 0 }
                    }
                    ?.map { "item: ${'$'}it" }
                    ?.forEach { println(it) }
            }
            
            class List<T> {
                fun filter(pred: (T) -> Boolean) : List<T> = TODO()
                fun <R> map(op: (T) -> R) : List<R> = TODO()
                fun all(op: (T) -> Boolean) : Boolean = TODO()
                fun forEach(op: (T) -> Unit) : Unit = TODO()
            }
            fun <T> T.takeIf(predicate: (T) -> Boolean): T? = TODO()
        """.trimIndent()

    override val description: String
        get() = KotlinBundle.message("inlay.kotlin.call.chains.hints")

    override fun isLanguageSupported(language: Language): Boolean = language == KotlinLanguage.INSTANCE

    override fun getProperty(key: String): String = KotlinBundle.message(key)

    override fun getCaseDescription(case: ImmediateConfigurable.Case): String? = case.extendedDescription

    override fun createFile(project: Project, fileType: FileType, document: Document): PsiFile =
        createKtFile(project, document, fileType)

    override fun KotlinType.getInlayPresentation(
        expression: PsiElement,
        factory: PresentationFactory,
        project: Project,
        context: BindingContext
    ): InlayPresentation {
        val inlayInfoDetails = HintsTypeRenderer
            .getInlayHintsTypeRenderer(context, expression as? KtElement ?: error("Only Kotlin psi are possible"))
            .renderTypeIntoInlayInfo(this)
        return getInlayPresentationForInlayInfoDetails(
            expression,
            null,
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
