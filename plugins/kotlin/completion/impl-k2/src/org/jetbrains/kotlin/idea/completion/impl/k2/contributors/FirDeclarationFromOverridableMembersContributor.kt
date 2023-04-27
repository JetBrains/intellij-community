// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.context.FirValueParameterPositionContext
import org.jetbrains.kotlin.idea.completion.contributors.keywords.OverrideKeywordHandler
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Completes names of override members. For example, in the following code:
 * ```
 * interface I {
 *      val someVal: Int
 *      fun foo()
 * }
 *
 * class A(override val <caret>): I
 *
 * class B: I {
 *      override fun <caret>
 * }
 * ```
 * `someVal` and `foo` will be suggested at the caret in class `A` and the caret at class `B` respectively.
 *
 * @see OverrideKeywordHandler
 */
internal class FirDeclarationFromOverridableMembersContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<FirRawPositionCompletionContext>(basicContext, priority) {
    override fun KtAnalysisSession.complete(positionContext: FirRawPositionCompletionContext, weighingContext: WeighingContext) {
        val declaration = when (positionContext) {
            is FirValueParameterPositionContext -> positionContext.ktParameter
            // In a fake file a callable declaration under construction is appended with "X.f$", which is parsed as a type reference.
            is FirTypeNameReferencePositionContext -> positionContext.typeReference?.let { getDeclarationFromReceiverTypeReference(it) }
            else -> null
        } ?: return

        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            val elements = with(OverrideKeywordHandler(basicContext)) { createOverrideMemberLookups(parameters, declaration, project) }
            sink.addAllElements(elements)
        }
    }

    private fun getDeclarationFromReceiverTypeReference(typeReference: KtTypeReference): KtCallableDeclaration? {
        return (typeReference.parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == typeReference }
    }
}