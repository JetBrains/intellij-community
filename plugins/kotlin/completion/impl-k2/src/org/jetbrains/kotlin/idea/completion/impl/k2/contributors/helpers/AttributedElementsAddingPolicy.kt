// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.idea.base.codeInsight.withContributorClass
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributor

// todo replace with a simple decorator/consumer
internal class AttributedElementsAddingPolicy(
    private val contributorClass: Class<FirCompletionContributor<*>>,
) : ElementsAddingPolicy.Default {

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
