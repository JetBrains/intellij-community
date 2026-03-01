// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.keywords.ActualKeywordHandler
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Completes names of actual declarations. For example, in the following code:
 *
 * ```
 * // example.kt
 * expect fun foo(): String
 * ```
 *
 * ```
 * // example.jvm.kt
 * actual<caret>
 * ```
 * `foo` will be suggested at the caret in the `example.jvm.kt` file.
 *
 * @see ActualKeywordHandler
 */
internal class K2ActualDeclarationContributor : K2SimpleCompletionContributor<KotlinTypeNameReferencePositionContext>(
    KotlinTypeNameReferencePositionContext::class
) {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinTypeNameReferencePositionContext>)
    override fun complete() {
        val declaration = context.positionContext.typeReference?.getDeclaration() ?: return
        if (!declaration.hasModifier(KtTokens.ACTUAL_KEYWORD)) return

        val elements = ActualKeywordHandler(
            importStrategyDetector = context.importStrategyDetector,
            declaration = declaration,
        ).createActualLookups(context.parameters, context.project)
        addElements(elements)
    }

    private fun KtTypeReference.getDeclaration(): KtCallableDeclaration? {
        val typeReference = this
        val declaration = typeReference.parent as? KtCallableDeclaration ?: return null

        if (declaration.receiverTypeReference == typeReference) return declaration

        return null
    }

    override fun K2CompletionSectionContext<KotlinTypeNameReferencePositionContext>.getGroupPriority(): Int {
        return 1
    }
}