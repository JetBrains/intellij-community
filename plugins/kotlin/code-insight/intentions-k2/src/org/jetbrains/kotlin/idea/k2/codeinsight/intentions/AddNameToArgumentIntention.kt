// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddArgumentNamesApplicators
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class AddNameToArgumentIntention :
    AbstractKotlinApplicatorBasedIntention<KtValueArgument, AddArgumentNamesApplicators.SingleArgumentInput>(KtValueArgument::class),
    LowPriorityAction {
    override fun getApplicator() = AddArgumentNamesApplicators.singleArgumentApplicator

    override fun getApplicabilityRange() = ApplicabilityRanges.VALUE_ARGUMENT_EXCLUDING_LAMBDA

    override fun getInputProvider() = inputProvider { element: KtValueArgument ->
        val argumentList = element.parent as? KtValueArgumentList ?: return@inputProvider null
        val shouldBeLastUnnamed =
            !element.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)
        if (shouldBeLastUnnamed && element != argumentList.arguments.last { !it.isNamed() }) return@inputProvider null

        val callElement = argumentList.parent as? KtCallElement ?: return@inputProvider null
        val resolvedCall = callElement.resolveCall().singleFunctionCallOrNull() ?: return@inputProvider null

        if (!resolvedCall.symbol.hasStableParameterNames) {
            return@inputProvider null
        }

        getArgumentNameIfCanBeUsedForCalls(element, resolvedCall)?.let(AddArgumentNamesApplicators::SingleArgumentInput)
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) = element is KtValueArgumentList ||
            element is KtContainerNode || super.skipProcessingFurtherElementsAfter(element)

}

fun KtAnalysisSession.getArgumentNameIfCanBeUsedForCalls(argument: KtValueArgument, resolvedCall: KtFunctionCall<*>): Name? {
    val valueParameterSymbol = resolvedCall.argumentMapping[argument.getArgumentExpression()]?.symbol ?: return null
    if (valueParameterSymbol.isVararg) {
        if (argument.languageVersionSettings.supportsFeature(LanguageFeature.ProhibitAssigningSingleElementsToVarargsInNamedForm) &&
            !argument.isSpread
        ) {
            return null
        }

        // We can only add the parameter name for an argument for a vararg parameter if it's the ONLY argument for the parameter. E.g.,
        //
        //   fun foo(vararg i: Int) {}
        //
        //   foo(1, 2) // Can NOT add `i = ` to either argument
        //   foo(1)    // Can change to `i = 1`
        val varargArgumentCount = resolvedCall.argumentMapping.values.count { it.symbol == valueParameterSymbol }
        if (varargArgumentCount != 1) {
            return null
        }
    }

    return valueParameterSymbol.name
}