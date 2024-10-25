// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.keywords.ActualKeywordHandler
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
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
internal class K2ActualDeclarationContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<KotlinRawPositionContext>(basicContext, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val declaration = when (positionContext) {
            is KotlinTypeNameReferencePositionContext -> positionContext.typeReference?.getDeclaration()
            else -> null
        } ?: return

        if (!declaration.hasModifier(KtTokens.ACTUAL_KEYWORD)) return

        val elements = ActualKeywordHandler(
            importStrategyDetector = importStrategyDetector,
            declaration = declaration,
        ).createActualLookups(parameters, project)
        sink.addAllElements(elements)
    }

    private fun KtTypeReference.getDeclaration(): KtCallableDeclaration? {
        val typeReference = this
        val declaration = typeReference.parent as? KtCallableDeclaration ?: return null

        if (declaration.receiverTypeReference == typeReference) return declaration

        return null
    }
}