// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertStringAndInvokeCompletion
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeConstraintNameInWhereClausePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render

internal class FirTypeParameterConstraintNameInWhereClauseCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinTypeConstraintNameInWhereClausePositionContext>(parameters, sink, priority) {

    context(KaSession)
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

private class TypeParameterInWhenClauseILookupObject(override val shortName: Name) : KotlinLookupObject


private object TypeParameterInWhenClauseInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val lookupElement = item.`object` as TypeParameterInWhenClauseILookupObject
        val name = lookupElement.shortName.render()
        context.document.replaceString(context.startOffset, context.tailOffset, name)
        context.commitDocument()
        context.insertStringAndInvokeCompletion(stringToInsert = " : ")
    }
}


