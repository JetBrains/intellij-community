// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.fir.api.AbstractHLIntention
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicatorInputProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.fir.applicators.AddArgumentNamesApplicators
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.getSingleCandidateSymbolOrNull
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.fir.applicators.AddArgumentNamesApplicators.SingleArgumentInput as Input

class HLAddNameToArgumentIntention :
    AbstractHLIntention<KtValueArgument, Input>(KtValueArgument::class, AddArgumentNamesApplicators.singleArgumentApplicator),
    LowPriorityAction {
    override val applicabilityRange = ApplicabilityRanges.VALUE_ARGUMENT_EXCLUDING_LAMBDA

    override val inputProvider: HLApplicatorInputProvider<KtValueArgument, Input> = inputProvider { element ->
        val argumentList = element.parent as? KtValueArgumentList ?: return@inputProvider null
        val shouldBeLastUnnamed = !element.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)
        if (shouldBeLastUnnamed && element != argumentList.arguments.last { !it.isNamed() }) return@inputProvider null

        val callElement = argumentList.parent as? KtCallElement ?: return@inputProvider null
        val resolvedCall = callElement.resolveCall() ?: return@inputProvider null

        if (resolvedCall.targetFunction.getSingleCandidateSymbolOrNull()?.hasStableParameterNames != true) {
            return@inputProvider null
        }

        getArgumentNameIfCanBeUsedForCalls(element, resolvedCall)?.let { name -> Input(name) }
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) = element is KtValueArgumentList ||
            element is KtContainerNode || super.skipProcessingFurtherElementsAfter(element)

    companion object {
        fun getArgumentNameIfCanBeUsedForCalls(argument: KtValueArgument, resolvedCall: KtCall): Name? {
            val valueParameterSymbol = resolvedCall.argumentMapping[argument.getArgumentExpression()] ?: return null
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
                val varargArgumentCount = resolvedCall.argumentMapping.values.count { it == valueParameterSymbol }
                if (varargArgumentCount != 1) {
                    return null
                }
            }

            return valueParameterSymbol.name
        }
    }
}