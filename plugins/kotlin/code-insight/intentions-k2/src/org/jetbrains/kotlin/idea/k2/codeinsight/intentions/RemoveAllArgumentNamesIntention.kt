// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.RemoveArgumentNamesApplicators
import org.jetbrains.kotlin.idea.parameterInfo.isArrayOfCall
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveAllArgumentNamesIntention :
    AbstractKotlinApplicatorBasedIntention<KtCallElement, RemoveArgumentNamesApplicators.MultipleArgumentsInput>(KtCallElement::class) {
    override fun getApplicator(): KotlinApplicator<KtCallElement, RemoveArgumentNamesApplicators.MultipleArgumentsInput> =
        RemoveArgumentNamesApplicators.multipleArgumentsApplicator

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtCallElement, RemoveArgumentNamesApplicators.MultipleArgumentsInput> =
        inputProvider { callElement ->
            val (sortedArguments, vararg, varargIsArrayOfCall) = collectSortedArgumentsThatCanBeUnnamed(callElement)
                ?: return@inputProvider null
            if (sortedArguments.isEmpty()) return@inputProvider null
            RemoveArgumentNamesApplicators.MultipleArgumentsInput(sortedArguments, vararg, varargIsArrayOfCall)
        }
}

class RemoveArgumentNameIntention :
    AbstractKotlinApplicatorBasedIntention<KtValueArgument, RemoveArgumentNamesApplicators.SingleArgumentInput>(KtValueArgument::class) {
    override fun getApplicator(): KotlinApplicator<KtValueArgument, RemoveArgumentNamesApplicators.SingleArgumentInput> =
        RemoveArgumentNamesApplicators.singleArgumentsApplicator

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgument> = applicabilityRange { valueArgument ->
        val argumentExpression = valueArgument.getArgumentExpression() ?: return@applicabilityRange null
        TextRange(valueArgument.startOffset, argumentExpression.startOffset).relativeTo(valueArgument)
    }

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtValueArgument, RemoveArgumentNamesApplicators.SingleArgumentInput> =
        inputProvider { computeInput(it) }

    private fun KtAnalysisSession.computeInput(valueArgument: KtValueArgument): RemoveArgumentNamesApplicators.SingleArgumentInput? {
        val callElement = valueArgument.getStrictParentOfType<KtCallElement>() ?: return null
        val (sortedArguments, vararg, varargIsArrayOfCall) = collectSortedArgumentsThatCanBeUnnamed(callElement) ?: return null
        if (valueArgument !in sortedArguments) return null

        val allArguments = callElement.valueArgumentList?.arguments ?: return null
        val sortedArgumentsBeforeCurrent = sortedArguments.takeWhile { it != valueArgument }

        val supportsMixed = valueArgument.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)
        val nameCannotBeRemoved = if (supportsMixed) {
            sortedArgumentsBeforeCurrent.withIndex().any { (parameterIndex, argument) -> parameterIndex != allArguments.indexOf(argument) }
        } else {
            sortedArgumentsBeforeCurrent.any { it.isNamed() }
        }
        if (nameCannotBeRemoved) return null

        return RemoveArgumentNamesApplicators.SingleArgumentInput(
            anchorArgument = sortedArgumentsBeforeCurrent.lastOrNull(),
            isVararg = valueArgument == vararg,
            isArrayOfCall = valueArgument == vararg && varargIsArrayOfCall
        )
    }
}

private data class ArgumentsData(val arguments: List<KtValueArgument>, val vararg: KtValueArgument?, val varargIsArrayOfCall: Boolean)

/**
 * Returns arguments that are not named or can be unnamed, placed on their correct positions.
 * No arguments following vararg argument are returned.
 */
private fun KtAnalysisSession.collectSortedArgumentsThatCanBeUnnamed(callElement: KtCallElement): ArgumentsData? {
    val resolvedCall = callElement.resolveCall().singleFunctionCallOrNull() ?: return null
    val valueArguments = callElement.valueArgumentList?.arguments ?: return null

    val argumentToParameterIndex = CallParameterInfoProvider.mapArgumentsToParameterIndices(
        callElement,
        resolvedCall.partiallyAppliedSymbol.signature,
        resolvedCall.argumentMapping
    )
    val argumentsOnCorrectPositions = valueArguments
        .sortedBy { argumentToParameterIndex[it.getArgumentExpression()] ?: Int.MAX_VALUE }
        .filterIndexed { index, argument -> index == argumentToParameterIndex[argument.getArgumentExpression()] }

    val vararg = argumentsOnCorrectPositions.firstOrNull {
        resolvedCall.argumentMapping[it.getArgumentExpression()]?.symbol?.isVararg == true
    }
    val varargIsArrayOfCall = (vararg?.getArgumentExpression() as? KtCallElement)?.let { isArrayOfCall(it) } == true

    val argumentsThatCanBeUnnamed = if (vararg != null) {
        val varargIndex = valueArguments.indexOf(vararg)
        // if an argument is vararg, it can only be unnamed if all arguments following it are named
        val takeVararg = valueArguments.drop(varargIndex + 1).all { it.isNamed() }
        argumentsOnCorrectPositions.take(if (takeVararg) varargIndex + 1 else varargIndex)
    } else {
        argumentsOnCorrectPositions
    }

    return ArgumentsData(argumentsThatCanBeUnnamed, vararg, varargIsArrayOfCall)
}