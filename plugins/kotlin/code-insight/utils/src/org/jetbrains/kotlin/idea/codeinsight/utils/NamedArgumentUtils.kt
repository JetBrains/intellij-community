// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.getCallElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

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
        return argumentsExcludingPrevious
            .associateWith { getNameForNameableArgument(it, resolvedCall) ?: return null }
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
        //((callElement.resolveToCall() as? KaErrorCallInfo).candidateCalls[0] as KaSimpleFunctionCall).symbol.hasStableParameterNames
        val resolvedCall =
            resolveToCall?.singleFunctionCallOrNull() ?: (resolveToCall as? KaErrorCallInfo)?.singleCallOrNull() ?: return null
        if (!resolvedCall.symbol.hasStableParameterNames) return null
        return getNameForNameableArgument(argument, resolvedCall)
    }

    private fun getNameForNameableArgument(argument: KtValueArgument, resolvedCall: KaFunctionCall<*>): Name? {
        val argumentMapping = resolvedCall.argumentMapping
        val variableSignature = argumentMapping[argument.getArgumentExpression()]
        if (variableSignature == null) {
            val resolvedCallSignatures = argumentMapping.values.map { it.symbol to it }.toMap()
            val name =
                resolvedCall.symbol.valueParameters.filter<KaValueParameterSymbol> { it !in resolvedCallSignatures }.firstOrNull()?.nameIfNotSpecial
            return name
        }
        val valueParameterSymbol = variableSignature.symbol
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
            val varargArgumentCount = argumentMapping.values.count { it.symbol == valueParameterSymbol }
            if (varargArgumentCount != 1) {
                return null
            }
        }

        return valueParameterSymbol.nameIfNotSpecial
    }
}

private val KaNamedSymbol.nameIfNotSpecial: Name?
    get() = name.takeUnless { it.isSpecial }
