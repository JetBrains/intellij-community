// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.application.options.CodeStyle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.formatter.KotlinCommonCodeStyleSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.util.isLineBreak
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
object SpecifyRemainingArgumentsByNameUtil {
    class RemainingArgumentsData(
        // The minimum list of arguments that are required to make the function call not be missing arguments
        val remainingRequiredArguments: List<Name>,
        // The list of all arguments that can be passed to the function call
        val allRemainingArguments: List<Name>,
    )

    /**
     * Adds a newline to the argument if there is no preceding newline and
     * the code style calls for named arguments to be passed on multiple lines.
     */
    private fun KtValueArgument.addNewlineBeforeIfNeeded(
        psiFactory: KtPsiFactory,
        codeStyle: KotlinCommonCodeStyleSettings
    ) {
        val argumentList = parent as? KtValueArgumentList ?: return
        // We want to skip the first newline for the parameter if the code style
        // does not require one after the left parenthesis
        if (argumentList.arguments.size == 1 && !codeStyle.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE) return
        // If we already have a newline before this argument, we do not need another one
        if ((prevSibling as? PsiWhiteSpace).isLineBreak()) return

        argumentList.addBefore(psiFactory.createNewLine(), this)
    }

    /**
     * Removes unnecessary double newlines at the end of the argument list if they exist.
     */
    private fun KtValueArgumentList.trimDoubleEndingNewlines(psiFactory: KtPsiFactory) {
        val rightParenthesis = rightParenthesis ?: return
        val prevWhitespace = rightParenthesis.prevSibling as? PsiWhiteSpace ?: return
        if (StringUtil.getLineBreakCount(prevWhitespace.text) > 1) {
            prevWhitespace.replace(psiFactory.createNewLine())
        }
    }

    /**
     * Returns false if [argumentMapping] contains arguments whose type conflicts with the type of the parameter.
     */
    private fun KaSession.isValidArgumentMapping(argumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>): Boolean {
        return argumentMapping.all {
            val expressionType = it.key.expressionType ?: return@all false
            it.value.returnType.hasCommonSubtypeWith(expressionType)
        }
    }

    /**
     * Calculates the [RemainingArgumentsData] for the call.
     * See [RemainingArgumentsData] for details.
     */
    private fun KaFunctionCall<*>.getRemainingArgumentsData(): RemainingArgumentsData? {
        if (!symbol.hasStableParameterNames) return null

        val specifiedArguments = argumentMapping.mapNotNull {
            it.value.name.takeIf { !it.isSpecial }?.identifier
        }.toSet()

        val validArguments = symbol.valueParameters.filter { parameter ->
            !parameter.name.isSpecial && parameter.name.identifier !in specifiedArguments && !parameter.isVararg
        }
        if (validArguments.isEmpty()) return null

        val withoutDefault = validArguments.filter { !it.hasDefaultValue }.map { it.name }
        return RemainingArgumentsData(withoutDefault, validArguments.map { it.name })
    }

    /**
     * Given the list of [allCalls] that are possible, this function returns the minimum required arguments
     * required to complete any of the calls and the most number of arguments that can be passed to any of the calls.
     */
    private fun KaSession.getRemainingArgumentsData(allCalls: List<KaCallCandidateInfo>): RemainingArgumentsData? {
        val allFunctionCalls = allCalls.map { info ->
            // If any of the calls cannot be resolved, we do not want to continue
            info.candidate as? KaFunctionCall<*> ?: return null
        }
        val validPossibleCalls = allFunctionCalls.filter { it.symbol.hasStableParameterNames && isValidArgumentMapping(it.argumentMapping) }
        if (validPossibleCalls.isEmpty()) return null

        val smallestData =
            validPossibleCalls.minBy { it.symbol.valueParameters.count { !it.hasDefaultValue } }.getRemainingArgumentsData() ?: return null
        val largestData = validPossibleCalls.maxBy { it.symbol.valueParameters.size }.getRemainingArgumentsData() ?: return null

        return RemainingArgumentsData(smallestData.remainingRequiredArguments, largestData.allRemainingArguments)
    }

    /**
     * Calculates the [RemainingArgumentsData] for the [element].
     */
    fun KaSession.findRemainingNamedArguments(element: KtValueArgumentList): RemainingArgumentsData? {
        val functionCall = element.parent as? KtCallExpression ?: return null
        val resolvedCall = functionCall.resolveToCall()?.singleFunctionCallOrNull()
        return if (resolvedCall != null) {
            // If we can unambiguously resolve the call, we get the data for it to avoid resolving all the candidates
            resolvedCall.getRemainingArgumentsData() ?: return null
        } else {
            getRemainingArgumentsData(functionCall.resolveToCallCandidates())
        }
    }

    /**
     * Adds the list of the [remainingArguments] to the [element] by passing it by name
     * with a placeholder template for each added argument.
     */
    fun applyFix(
        project: Project,
        element: KtValueArgumentList,
        remainingArguments: List<Name>,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(project, markGenerated = true)
        val templateFields = mutableListOf<KtExpression>()

        val codeStyle = CodeStyle.getSettings(project).kotlinCommonSettings

        for (remainingArgument in remainingArguments) {
            val todoExpression = psiFactory.createExpression("TODO()")
            val argument = psiFactory.createArgument(expression = todoExpression, name = remainingArgument)
            val addedArgument = element.addArgument(argument)
            addedArgument.addNewlineBeforeIfNeeded(psiFactory, codeStyle)

            val addedArgumentExpression = addedArgument.getArgumentExpression() ?: continue
            templateFields.add(addedArgumentExpression)
        }

        // If the user places their cursor in an empty argument-list that is already setup for
        // multiple lines, like foo(\n<caret>\n)
        // then adding the arguments leaves an additional line break at the end, which we want to remove
        element.trimDoubleEndingNewlines(psiFactory)
        element.reformat(canChangeWhiteSpacesOnly = true)

        // Create a template that allows the user to change each inserted todo to an actual value
        updater.templateBuilder().apply {
            for (todoExpression in templateFields) {
                field(todoExpression, todoExpression.text)
            }
        }
    }
}