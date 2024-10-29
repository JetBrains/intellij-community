// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.codeInsight.withContributorClass
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributor
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext

private class AttributedElementsAddingPolicy private constructor(
    private val contributorClass: Class<FirCompletionContributor<*>>,
) : ElementsAddingPolicy.Default {

    constructor(contributor: FirCompletionContributor<*>) : this(contributor.javaClass)

    override fun addElement(
        result: CompletionResultSet,
        element: LookupElement,
    ) = super.addElement(
        result = result,
        element = element.withContributorClass(contributorClass),
    )

    override fun addAllElements(
        result: CompletionResultSet,
        elements: Iterable<LookupElement>,
    ) = super.addAllElements(
        result = result,
        elements = elements.map { it.withContributorClass(contributorClass) },
    )

}

internal fun <C : KotlinRawPositionContext> FirCompletionContributor<C>.withPolicyController(
    policyController: PolicyController,
): FirCompletionContributor<C> = object : FirCompletionContributor<C> {

    context(KaSession)
    override fun complete(
        positionContext: C,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        policyController.invokeWithPolicy(AttributedElementsAddingPolicy(this@withPolicyController)) {
            this@withPolicyController.complete(
                positionContext,
                weighingContext,
                sessionParameters,
            )
        }
    }
}