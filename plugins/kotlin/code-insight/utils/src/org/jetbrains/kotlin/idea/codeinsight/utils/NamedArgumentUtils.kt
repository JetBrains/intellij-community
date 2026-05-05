// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.varargArrayType
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.getCallElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import kotlin.collections.firstOrNull
import kotlin.collections.get

object NamedArgumentUtils {
    fun addArgumentName(element: KtValueArgument, argumentName: Name) {
        val argumentExpression = element.getArgumentExpression() ?: return

        val prevSibling = element.getPrevSiblingIgnoringWhitespace()
        if (prevSibling is PsiComment && """/\*\s*$argumentName\s*=\s*\*/""".toRegex().matches(prevSibling.text)) {
            prevSibling.delete()
        }

        val newArgument = KtPsiFactory(element).createArgument(argumentExpression, argumentName, element.getSpreadElement() != null)
        element.replace(newArgument)
    }

    fun addArgumentNames(argumentNames: Map<KtValueArgument, Name>) {
        for ((argument, name) in argumentNames) {
            addArgumentName(argument, name)
        }
    }

    /**
     * Associates each argument of [call] with its argument name if `argumentName = argument` is valid for all arguments. Optionally,
     * starts at [startArgument] if it's not `null`.
     */
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    fun associateArgumentNamesStartingAt(
        call: KtCallElement,
        startArgument: KtValueArgument?
    ): Map<SmartPsiElementPointer<KtValueArgument>, Name>? {
        val resolvedCall = call.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        if (!resolvedCall.symbol.hasStableParameterNames) {
            return null
        }

        val arguments = call.valueArgumentList?.arguments ?: return null
        val argumentsExcludingPrevious = if (startArgument != null) arguments.dropWhile { it != startArgument } else arguments
        val reservedNames = mutableSetOf<Name>()
        return argumentsExcludingPrevious
            .associateWith {
                val name = getNameForNameableArgument(it, call, resolvedCall, reservedNames)
                if (name != null) {
                    reservedNames.add(name)
                    name
                } else {
                    return null
                }
            }
            .mapKeys { it.key.createSmartPointer() }
    }

    /**
     * Returns the name of the value argument if it can be used for calls.
     * The method also works for [argument] that is [KtLambdaArgument], since
     * the argument name can be used after moving [KtLambdaArgument] inside parentheses.
     */
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    fun getStableNameFor(argument: KtValueArgument): Name? {
        val callElement: KtCallElement = getCallElement(argument) ?: return null
        val resolveToCall = callElement.resolveToCall()
        val resolvedCall = resolveToCall?.singleFunctionCallOrNull() ?: (resolveToCall as? KaErrorCallInfo)?.singleCallOrNull() ?: return null
        if (!resolvedCall.symbol.hasStableParameterNames) return null
        return getNameForNameableArgument(argument, callElement, resolvedCall)
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun getNameForNameableArgument(
        argument: KtValueArgument,
        callElement: KtCallElement,
        resolvedCall: KaFunctionCall<*>,
        reservedNames: Set<Name>? = null
    ): Name? {
        // if we have only 1 vararg param it's not an ambiguity
        if (hasAmbiguousVararg(argument, resolvedCall)) return null

        val allArguments = callElement.valueArgumentList?.arguments ?: return null
        val nameForValueParameter = getNameForValueParameter(argument, allArguments, resolvedCall, reservedNames)
        if (nameForValueParameter != null) return nameForValueParameter

        val contextParameters = resolvedCall.symbol.contextParameters
        if (contextParameters.isNotEmpty()) {
            return getNameForContextParameter(argument, allArguments, resolvedCall, contextParameters, reservedNames)
        }
        return null
    }

    @OptIn(KaExperimentalApi::class, KaContextParameterApi::class)
    context(_: KaSession)
    private fun isMappingBroken(resolvedCall: KaFunctionCall<*>, argument: KtValueArgument): Boolean {
        val valueArgumentMapping = resolvedCall.valueArgumentMapping
        val contextArgumentMapping = resolvedCall.contextArgumentMapping
        val argumentExpression = argument.getArgumentExpression()
        val realArgumentType = argumentExpression?.expressionType
        val associatedValue = valueArgumentMapping[argumentExpression] ?: contextArgumentMapping[argumentExpression] ?: return true

        val associatedType = when (val symbol = associatedValue.symbol) {
            is KaValueParameterSymbol -> {
                if (symbol.isVararg && argument.isSpread) {
                    symbol.varargArrayType ?: return true
                } else {
                    associatedValue.returnType
                }
            }
            is KaContextParameterSymbol -> associatedValue.returnType
            else -> return true
        }
        return realArgumentType?.isSubtypeOf(associatedType) != true
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun hasAmbiguousVararg(argument: KtValueArgument, resolvedCall: KaFunctionCall<*>): Boolean {
        val varargParam = resolvedCall.symbol.valueParameters.find { it.isVararg } ?: return false
        val argumentType = argument.getArgumentExpression()?.expressionType ?: return false
        if (!argumentType.isSubtypeOf(varargParam.returnType)) return false
        val varargArgCount = resolvedCall.valueArgumentMapping.values.count { it.symbol == varargParam }
        return varargArgCount > 1
    }

    private fun isParamNameAvailable(argument: KtValueArgument, allArguments: List<KtValueArgument>, paramName: Name): Boolean {
        return allArguments.none { it != argument && it.getArgumentName()?.asName == paramName }
    }

    private fun canNameVarargParam(argument: KtValueArgument, isVararg: Boolean): Boolean {
        if (!isVararg) return true
        if (argument.isSpread) return true
        return !argument.languageVersionSettings.supportsFeature(LanguageFeature.ProhibitAssigningSingleElementsToVarargsInNamedForm)
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun getNameForValueParameter(
        argument: KtValueArgument,
        allArguments: List<KtValueArgument>,
        resolvedCall: KaFunctionCall<*>,
        reservedNames: Set<Name>? = null
    ): Name? {
        val valueArgumentMapping = resolvedCall.valueArgumentMapping
        val mappingBroken = isMappingBroken(resolvedCall, argument)

        if (!mappingBroken) {
            // if argument is mapped, use the mapped param
            val mappedValueParam = valueArgumentMapping[argument.getArgumentExpression()]
            if (mappedValueParam != null) {
                val valueParameterSymbol = mappedValueParam.symbol
                val paramName = valueParameterSymbol.name
                if (!isParamNameAvailable(argument, allArguments, paramName) || paramName in reservedNames.orEmpty()) return null
                if (!canNameVarargParam(argument, valueParameterSymbol.isVararg)) return null
                return valueParameterSymbol.nameIfNotSpecial
            }
        } else {
            // argument is not mapped or mapping is broken (error case) - find first type-matching value parameter with available name
            val mappedSymbols = valueArgumentMapping.values.associateBy { it.symbol }
            val argumentType = argument.getArgumentExpression()?.expressionType ?: return null
            return resolvedCall.symbol.valueParameters.firstOrNull {
                it !in mappedSymbols
                        && isParamNameAvailable(argument, allArguments, it.name)
                        && argumentType.isSubtypeOf(it.returnType)
                        && it.name !in reservedNames.orEmpty()
                        && canNameVarargParam(argument, it.isVararg)
            }?.nameIfNotSpecial
        }
        return null
    }

    @OptIn(KaExperimentalApi::class, KaContextParameterApi::class)
    context(_: KaSession)
    private fun getNameForContextParameter(
        argument: KtValueArgument,
        allArguments: List<KtValueArgument>,
        resolvedCall: KaFunctionCall<*>,
        contextParameters: List<KaContextParameterSymbol>,
        reservedNames: Set<Name>? = null
    ): Name? {
        val contextArgumentMapping = resolvedCall.contextArgumentMapping
        val argumentType = argument.getArgumentExpression()?.expressionType ?: return null

        // if argument is mapped to a context param, use that
        val mappedContextParam = contextArgumentMapping[argument.getArgumentExpression()]
        if (mappedContextParam != null) {
            val paramName = mappedContextParam.symbol.name
            if (!isParamNameAvailable(argument, allArguments, paramName) || paramName in reservedNames.orEmpty()) return null
            if (!argumentType.isSubtypeOf(mappedContextParam.returnType)) return null
            return mappedContextParam.symbol.nameIfNotSpecial
        }

        // argument is not mapped (error case) - find first type-matching context parameter with available name
        val mappedContextSymbols = contextArgumentMapping.values.associateBy { it.symbol }

        return contextParameters.firstOrNull {
            it !in mappedContextSymbols
                    && isParamNameAvailable(argument, allArguments, it.name)
                    && argumentType.isSubtypeOf(it.returnType)
                    && it.name !in reservedNames.orEmpty()
        }?.nameIfNotSpecial
    }
}

private val KaNamedSymbol.nameIfNotSpecial: Name?
    get() = name.takeUnless { it.isSpecial }
