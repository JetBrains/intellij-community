// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddArgumentNamesApplicators
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class AddNamesToFollowingArgumentsIntention :
    AbstractKotlinApplicatorBasedIntention<KtValueArgument, AddArgumentNamesApplicators.MultipleArgumentsInput>(KtValueArgument::class),
    LowPriorityAction {
    override fun getApplicabilityRange() = ApplicabilityRanges.VALUE_ARGUMENT_EXCLUDING_LAMBDA

    override fun getApplicator() = applicator<KtValueArgument, AddArgumentNamesApplicators.MultipleArgumentsInput> {
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


    override fun getInputProvider() = inputProvider { element: KtValueArgument ->
        val argumentList = element.parent as? KtValueArgumentList ?: return@inputProvider null

        val callElement = argumentList.parent as? KtCallElement ?: return@inputProvider null
        val resolvedCall = callElement.resolveCall().singleFunctionCallOrNull() ?: return@inputProvider null

        if (!resolvedCall.symbol.hasStableParameterNames) {
            return@inputProvider null
        }

        val argumentsExcludingPrevious =
            callElement.valueArgumentList?.arguments?.dropWhile { it != element } ?: return@inputProvider null
        AddArgumentNamesApplicators.MultipleArgumentsInput(argumentsExcludingPrevious.associateWith {
            getArgumentNameIfCanBeUsedForCalls(it, resolvedCall) ?: return@inputProvider null
        })
    }
}