// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalElementOfSelf
import org.jetbrains.kotlin.idea.completion.implCommon.OperatorNameCompletion
import org.jetbrains.kotlin.idea.completion.lookups.factories.OperatorNameLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext

internal class FirOperatorNameCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinTypeNameReferencePositionContext>(
    parameters,
    sink,
    priority,
) {
    context(KaSession)
    override fun complete(
        positionContext: KotlinTypeNameReferencePositionContext,
        weighingContext: WeighingContext
    ) {
        val isApplicable = OperatorNameCompletion.isPositionApplicable(
            nameExpression = positionContext.nameExpression,
            expression = positionContext.nameExpression,
            position = positionContext.position
        ) {
            getOriginalElementOfSelf(it, parameters.originalFile)
        }
        if (!isApplicable) return

        OperatorNameCompletion.getApplicableOperators {
            prefixMatcher.prefixMatches(it)
        }.map(OperatorNameLookupElementFactory::createLookup)
            .forEach(sink::addElement)
    }
}