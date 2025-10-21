// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isArrayOfCall
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument

object RemoveArgumentNamesUtils {
    /**
     * @property sortedArguments arguments that can be unnamed or already don't have any names;
     * the arguments are sorted by the indices of respective parameters and should be placed in the beginning of argument list
     */
    data class ArgumentsData(val sortedArguments: List<KtValueArgument>, val vararg: KtValueArgument?, val varargIsArrayOfCall: Boolean)

    /**
     * Returns arguments that are not named or can be unnamed, placed on their correct positions.
     * No arguments following vararg argument are returned.
     */
    context(_: KaSession)
    fun collectSortedArgumentsThatCanBeUnnamed(callElement: KtCallElement): ArgumentsData? {
        val resolvedCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
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
}