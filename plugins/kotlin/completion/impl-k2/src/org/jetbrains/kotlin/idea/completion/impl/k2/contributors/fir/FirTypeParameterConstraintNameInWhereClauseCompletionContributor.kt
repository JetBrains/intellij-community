// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.fir

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributorBase
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.TypeParameterInWhenClauseILookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.TypeParameterInWhenClauseInsertionHandler
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeConstraintNameInWhereClausePositionContext

internal class FirTypeParameterConstraintNameInWhereClauseCompletionContributor(
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinTypeConstraintNameInWhereClausePositionContext>(sink, priority) {

    context(_: KaSession)
    override fun complete(
        positionContext: KotlinTypeConstraintNameInWhereClausePositionContext,
        weighingContext: WeighingContext,
    ) {
        val ownerSymbol = positionContext.typeParametersOwner.symbol

        @OptIn(KaExperimentalApi::class)
        ownerSymbol.typeParameters.forEach { typeParameter ->
            val name = typeParameter.name
            val icon = getIconFor(typeParameter)
            LookupElementBuilder.create(TypeParameterInWhenClauseILookupObject(name), name.asString())
                .withTailText(" : ")
                .withInsertHandler(TypeParameterInWhenClauseInsertionHandler)
                .withPsiElement(typeParameter.psi)
                .withIcon(icon)
                .let(sink::addElement)
        }
    }
}