// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isArrayOfCall
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument

object RemoveArgumentNamesUtils {
    /**
     * @property argumentsThatCanBeUnnamed arguments that can be unnamed or already don't have any names;
     * the arguments are sorted by the indices of respective parameters and should be placed in the beginning of argument list
     * @property contextArguments context arguments with their parameter names; these should be placed at the end with explicit names
     */
    data class ArgumentsData(
        val argumentsThatCanBeUnnamed: List<KtValueArgument>,
        val vararg: KtValueArgument?,
        val varargIsArrayOfCall: Boolean,
        val contextArguments: List<Pair<KtValueArgument, Name>> = emptyList(),
        val hasUnmappedArguments: Boolean = false,
    )

    /**
     * Returns arguments that are not named or can be unnamed, placed in their correct positions.
     * No arguments following vararg argument are returned.
     */
    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun collectArgumentsContext(callElement: KtCallElement): ArgumentsData? {
        val resolvedCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val valueArguments = callElement.valueArgumentList?.arguments ?: return null

        val argumentToParameterIndex = CallParameterInfoProvider.mapArgumentsToParameterIndices(
            callElement,
            resolvedCall.signature,
            resolvedCall.valueArgumentMapping
        )

        val contextArguments = mutableListOf<Pair<KtValueArgument, Name>>()
        val argumentByExpression = valueArguments.associateBy { it.getArgumentExpression() }

        val explicitContextArgs = resolvedCall.contextArgumentMapping
        for ((expr, signature) in explicitContextArgs) {
            val argument = argumentByExpression[expr]
            if (argument != null) {
                contextArguments.add(argument to signature.symbol.name)
            }
        }
        val contextArgSet = contextArguments.mapTo(hashSetOf()) { it.first }
        val contextParameterNames = contextArguments.mapTo(hashSetOf()) { it.second }

        // red code check (we will not suggest removing all in case of already existing ambiguity)
        val hasUnmappedArguments = valueArguments.any {
            it !in contextArgSet && it.getArgumentExpression() !in argumentToParameterIndex
        }

        val valueOnlyArguments = valueArguments.filter {
            val argName = it.getArgumentName()?.asName
            // Include if: argument is in value parameter mapping AND not a context argument AND name doesn't match context param
            it !in contextArgSet &&
                    it.getArgumentExpression() in argumentToParameterIndex &&
                    (argName == null || argName !in contextParameterNames)
        }

        val argumentsOnCorrectPositions = valueOnlyArguments
            .sortedBy { argumentToParameterIndex[it.getArgumentExpression()] ?: Int.MAX_VALUE }
            .filterIndexed { index, argument -> index == argumentToParameterIndex[argument.getArgumentExpression()] }

        val vararg = argumentsOnCorrectPositions.firstOrNull {
            resolvedCall.valueArgumentMapping[it.getArgumentExpression()]?.symbol?.isVararg == true
        }
        val varargIsArrayOfCall = (vararg?.getArgumentExpression() as? KtCallElement)?.let { isArrayOfCall(it) } == true

        val argumentsThatCanBeUnnamed = if (vararg != null) {
            val varargIndex = argumentsOnCorrectPositions.indexOf(vararg)

            // if an argument is vararg, it can only be unnamed if all arguments following it (in param order) are named
            val takeVararg = argumentsOnCorrectPositions.drop(varargIndex + 1).all { it.isNamed() }
            argumentsOnCorrectPositions.take(if (takeVararg) varargIndex + 1 else varargIndex)
        } else {
            argumentsOnCorrectPositions
        }

        return ArgumentsData(argumentsThatCanBeUnnamed, vararg, varargIsArrayOfCall, contextArguments, hasUnmappedArguments)
    }
}