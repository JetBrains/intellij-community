// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalElementOfSelf
import org.jetbrains.kotlin.idea.completion.implCommon.OperatorNameCompletion
import org.jetbrains.kotlin.idea.completion.lookups.factories.OperatorNameLookupElementFactory
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext

internal class K2OperatorNameCompletionContributor : K2SimpleCompletionContributor<KotlinTypeNameReferencePositionContext>(
    KotlinTypeNameReferencePositionContext::class
) {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinTypeNameReferencePositionContext>)
    override fun complete() {
        val positionContext = context.positionContext
        val isApplicable = OperatorNameCompletion.isPositionApplicable(
            nameExpression = positionContext.nameExpression,
            expression = positionContext.nameExpression,
            position = positionContext.position
        ) {
            getOriginalElementOfSelf(it, context.parameters.originalFile)
        }
        if (!isApplicable) return

        OperatorNameCompletion.getApplicableOperators {
            context.prefixMatcher.prefixMatches(it)
        }.map(OperatorNameLookupElementFactory::createLookup)
            .forEach { addElement(it) }
    }
}