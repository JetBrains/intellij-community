// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.intentions

import com.intellij.openapi.util.TextRange
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
import org.jetbrains.kotlin.idea.frontend.api.calls.getSingleCandidateSymbolOrNull
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class HLAddNamesToCallArgumentsIntention :
    AbstractHLIntention<KtCallElement, HLAddNamesToCallArgumentsIntention.Input>(KtCallElement::class, applicator) {
    class Input(val argumentNames: Map<KtValueArgument, Name>) : HLApplicatorInput

    override val applicabilityRange: HLApplicabilityRange<KtCallElement> = applicabilityRanges { element ->
        // Note: Applicability range matches FE 1.0 (see AddNamesToCallArgumentsIntention).
        val calleeExpression = element.calleeExpression ?: return@applicabilityRanges emptyList()
        val calleeExpressionTextRange = calleeExpression.textRangeIn(element)
        val arguments = element.valueArguments
        if (arguments.size < 2) {
            listOf(calleeExpressionTextRange)
        } else {
            val firstArgument = arguments.firstOrNull() as? KtValueArgument ?: return@applicabilityRanges emptyList()
            val endOffset = firstArgument.textRangeIn(element).endOffset
            listOf(TextRange(calleeExpressionTextRange.startOffset, endOffset))
        }
    }

    override val inputProvider: HLApplicatorInputProvider<KtCallElement, Input> = inputProvider { element ->
        val resolvedCall = element.resolveCall() as? KtCallWithArguments ?: return@inputProvider null

        if (resolvedCall.targetFunction.getSingleCandidateSymbolOrNull()?.hasStableParameterNames != true) {
            return@inputProvider null
        }

        val arguments = element.valueArgumentList?.arguments ?: return@inputProvider null
        Input(arguments.associateWith {
            HLAddNameToArgumentIntention.getArgumentNameIfCanBeUsedForCalls(it, resolvedCall) ?: return@inputProvider null
        })
    }

    companion object {
        val applicator = applicator<KtCallElement, Input> {
            familyAndActionName(KotlinBundle.lazyMessage("add.names.to.call.arguments"))

            isApplicableByPsi { element ->
                element.valueArguments.any { !it.isNamed() && it !is LambdaArgument }
            }

            applyTo { element, input, project, editor ->
                for ((argument, name) in input.argumentNames) {
                    HLAddNameToArgumentIntention.applicator.applyTo(argument, HLAddNameToArgumentIntention.Input(name), project, editor)
                }
            }
        }
    }
}