// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.keywords.OverrideKeywordHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPrimaryConstructorParameterPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinValueParameterPositionContext
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
internal class K2DeclarationFromOverridableMembersContributor : K2SimpleCompletionContributor<KotlinRawPositionContext>(
    KotlinRawPositionContext::class
) {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    override fun complete() {
        val positionContext = context.positionContext
        val declaration = when (positionContext) {
            is KotlinValueParameterPositionContext -> positionContext.ktParameter
            // In a fake file a callable declaration under construction is appended with "X.f$", which is parsed as a type reference.
            is KotlinTypeNameReferencePositionContext -> positionContext.typeReference?.let { getDeclarationFromReceiverTypeReference(it) }
            else -> null
        } ?: return

        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            val elements = OverrideKeywordHandler(context.importStrategyDetector)
                .createOverrideMemberLookups(context.parameters, declaration, context.project)
            addElements(elements)
        }
    }

    private fun getDeclarationFromReceiverTypeReference(typeReference: KtTypeReference): KtCallableDeclaration? {
        return (typeReference.parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == typeReference }
    }

    override fun K2CompletionSectionContext<KotlinRawPositionContext>.getGroupPriority(): Int = when (positionContext) {
        is KotlinTypeNameReferencePositionContext -> 1
        else -> 0
    }

    override fun K2CompletionSetupScope<KotlinRawPositionContext>.isAppropriatePosition(): Boolean = when (position) {
        is KotlinTypeNameReferencePositionContext,
        is KotlinPrimaryConstructorParameterPositionContext -> true

        else -> false
    }
}