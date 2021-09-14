// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.AbstractHLIntention
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicatorInputProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.fir.applicators.AddArgumentNamesApplicators
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.analysis.api.calls.getSingleCandidateSymbolOrNull
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.idea.fir.applicators.AddArgumentNamesApplicators.MultipleArgumentsInput as Input

class HLAddNamesToFollowingArgumentsIntention :
    AbstractHLIntention<KtValueArgument, Input>(KtValueArgument::class, applicator), LowPriorityAction {
    override val applicabilityRange = ApplicabilityRanges.VALUE_ARGUMENT_EXCLUDING_LAMBDA

    override val inputProvider: HLApplicatorInputProvider<KtValueArgument, Input> = inputProvider { element ->
        val argumentList = element.parent as? KtValueArgumentList ?: return@inputProvider null

        val callElement = argumentList.parent as? KtCallElement ?: return@inputProvider null
        val resolvedCall = callElement.resolveCall() ?: return@inputProvider null

        if (resolvedCall.targetFunction.getSingleCandidateSymbolOrNull()?.hasStableParameterNames != true) {
            return@inputProvider null
        }

        val argumentsExcludingPrevious = callElement.valueArgumentList?.arguments?.dropWhile { it != element } ?: return@inputProvider null
        Input(argumentsExcludingPrevious.associateWith {
            HLAddNameToArgumentIntention.getArgumentNameIfCanBeUsedForCalls(it, resolvedCall) ?: return@inputProvider null
        })
    }

    companion object {
        private val applicator = applicator<KtValueArgument, Input> {
            familyAndActionName(KotlinBundle.lazyMessage("add.names.to.this.argument.and.following.arguments"))

            isApplicableByPsi { element ->
                // Not applicable when lambda is trailing lambda after argument list (e.g., `run {  }`); element is a KtLambdaArgument.
                // May be applicable when lambda is inside an argument list (e.g., `run({  })`); element is a KtValueArgument in this case.
                if (element.isNamed() || element is KtLambdaArgument) {
                    return@isApplicableByPsi false
                }

                val argumentList = element.parent as? KtValueArgumentList ?: return@isApplicableByPsi false
                // Shadowed by HLAddNamesToCallArgumentsIntention
                if (argumentList.arguments.firstOrNull() == element) return@isApplicableByPsi false
                // Shadowed by HLAddNameToArgumentIntention
                if (argumentList.arguments.lastOrNull { !it.isNamed() } == element) return@isApplicableByPsi false
                true
            }

            applyTo { element, input, project, editor ->
                val callElement = element.parent?.safeAs<KtValueArgumentList>()?.parent as? KtCallElement
                callElement?.let { AddArgumentNamesApplicators.multipleArgumentsApplicator.applyTo(it, input, project, editor) }
            }
        }
    }
}