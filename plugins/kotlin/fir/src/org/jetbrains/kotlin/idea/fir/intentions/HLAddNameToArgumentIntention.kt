// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.AbstractHLIntention
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicabilityRange
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicatorInputProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.applicabilityRanges
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.frontend.api.calls.KtCallWithArguments
import org.jetbrains.kotlin.idea.frontend.api.calls.KtFunctionCall
import org.jetbrains.kotlin.idea.frontend.api.calls.getSingleCandidateSymbolOrNull
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

class HLAddNameToArgumentIntention :
    AbstractHLIntention<KtValueArgument, HLAddNameToArgumentIntention.Input>(KtValueArgument::class, applicator), LowPriorityAction {
    class Input(val name: Name) : HLApplicatorInput

    override val applicabilityRange: HLApplicabilityRange<KtValueArgument> = applicabilityRanges { element ->
        val expression = element.getArgumentExpression() ?: return@applicabilityRanges emptyList()
        if (expression is KtLambdaExpression) {
            // Use OUTSIDE of curly braces only as applicability ranges for lambda inside an argument list (e.g., `run({  })`).
            // If we use the text range of the curly brace elements, it will include the inside of the braces. This matches FE 1.0 behavior.
            // Note: Intention is NOT applicable when lambda is trailing lambda after argument list (e.g., `run {  }`).
            listOfNotNull(TextRange(0, 0), TextRange(element.textLength, element.textLength))
        } else {
            listOf(TextRange(0, element.textLength))
        }
    }

    override val inputProvider: HLApplicatorInputProvider<KtValueArgument, Input> = inputProvider { element ->
        val argumentList = element.parent as? KtValueArgumentList ?: return@inputProvider null
        val shouldBeLastUnnamed = !element.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)
        if (shouldBeLastUnnamed && element != argumentList.arguments.last { !it.isNamed() }) return@inputProvider null

        val callElement = argumentList.parent as? KtCallElement ?: return@inputProvider null
        val resolvedCall = callElement.resolveCall() as? KtCallWithArguments ?: return@inputProvider null

        if (resolvedCall.targetFunction.getSingleCandidateSymbolOrNull()?.hasStableParameterNames != true) {
            return@inputProvider null
        }

        val valueParameterSymbol = resolvedCall.argumentMapping[element] ?: return@inputProvider null
        if (valueParameterSymbol.isVararg) {
            if (element.languageVersionSettings.supportsFeature(LanguageFeature.ProhibitAssigningSingleElementsToVarargsInNamedForm) &&
                !element.isSpread) {
                return@inputProvider null
            }

            // We can only add the parameter name for an argument for a vararg parameter if it's the ONLY argument for the parameter. E.g.,
            //
            //   fun foo(vararg i: Int) {}
            //
            //   foo(1, 2) // Can NOT add `i = ` to either argument
            //   foo(1)    // Can change to `i = 1`
            val varargArgumentCount = resolvedCall.argumentMapping.values.count { it == valueParameterSymbol }
            if (varargArgumentCount != 1) {
                return@inputProvider null
            }
        }

        Input(valueParameterSymbol.name)
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) = element is KtValueArgumentList ||
            element is KtContainerNode || super.skipProcessingFurtherElementsAfter(element)

    companion object {
        val applicator = applicator<KtValueArgument, Input> {
            familyName(KotlinBundle.lazyMessage("add.name.to.argument"))

            actionName { _, input -> KotlinBundle.message("add.0.to.argument", input.name)  }

            isApplicableByPsi { element ->
                // Not applicable when lambda is trailing lambda after argument list (e.g., `run {  }`).
                // Note: Intention IS applicable when lambda is inside an argument list (e.g., `run({  })`).
                !element.isNamed() && element !is KtLambdaArgument
            }

            applyTo { element, input ->
                val expression = element.getArgumentExpression() ?: return@applyTo
                val name = input.name

                val prevSibling = element.getPrevSiblingIgnoringWhitespace()
                if (prevSibling is PsiComment && """/\*\s*$name\s*=\s*\*/""".toRegex().matches(prevSibling.text)) {
                    prevSibling.delete()
                }

                val newArgument = KtPsiFactory(element).createArgument(expression, name, element.getSpreadElement() != null)
                element.replace(newArgument)
            }
        }
    }
}