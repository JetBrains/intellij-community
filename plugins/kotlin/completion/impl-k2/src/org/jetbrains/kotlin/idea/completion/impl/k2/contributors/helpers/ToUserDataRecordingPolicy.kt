// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.codeInsight.LOOKUP_ELEMENT_CONTRIBUTOR
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributor
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext

internal class ToUserDataRecordingPolicy(private val contributor: FirCompletionContributor<*>) : ElementsAddingPolicy.Default {
    override fun addElement(result: CompletionResultSet, element: LookupElement) {
        result.addElement(element.decorate())
    }

    override fun addAllElements(result: CompletionResultSet, elements: Iterable<LookupElement>) {
        elements.forEach { it.decorate() }
        result.addAllElements(elements)
    }

    private fun LookupElement.decorate(): LookupElement {
        this.putUserData(LOOKUP_ELEMENT_CONTRIBUTOR, contributor.javaClass)
        return this
    }
}

internal class RecordingFirCompletionContributorDelegate<C : FirRawPositionCompletionContext>(
    private val originalContributor: FirCompletionContributor<C>,
    private val resultController: PolicyController,
) : FirCompletionContributor<C> {
    context(KtAnalysisSession)
    override fun complete(
        positionContext: C,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters
    ) {
        resultController.invokeWithPolicy(ToUserDataRecordingPolicy(originalContributor)) {
            originalContributor.complete(positionContext, weighingContext, sessionParameters)
        }
    }
}

internal fun <C : FirRawPositionCompletionContext> FirCompletionContributor<C>.recording(resultController: PolicyController): FirCompletionContributor<C> {
    return RecordingFirCompletionContributorDelegate(this, resultController)
}