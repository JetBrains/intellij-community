// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.application.options.CodeStyle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.resolution.KaCallCandidate
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.useSiteModule
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.psi.appendValueArgument
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.formatter.KotlinCommonCodeStyleSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.util.isLineBreak
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExperimentalApi
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

@ApiStatus.Internal
object SpecifyRemainingArgumentsByNameUtil {
    class RemainingArgumentsData(
        // The minimum list of arguments that are required to make the function call not be missing arguments
        val remainingRequiredArguments: List<Name>,
        // The list of all value arguments that can be passed to the function call
        val allValueRemainingArguments: List<Name>,
        // The list of all context arguments that can be passed to the function call
        val allContextRemainingArguments: List<Name> = emptyList(),
        // All context parameter names (for identifying existing context args in the call)
        val allContextParameterNames: Set<Name> = emptySet(),
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
     * Adds arguments with TODO() placeholders to the argument list.
     * If [anchor] is provided, arguments are inserted before it, otherwise they are appended at the end.
     * Returns the list of added TODO() expressions for template fields.
     * If [suggestions] is non-empty, the suggested expression is used in place of TODO().
     */
    private fun addArguments(
        argumentNames: List<Name>,
        element: KtValueArgumentList,
        anchor: KtValueArgument?,
        psiFactory: KtPsiFactory,
        codeStyle: KotlinCommonCodeStyleSettings,
        suggestions: Map<Name, Name> = emptyMap()
    ): List<KtExpression> {
        return argumentNames.mapNotNull { argumentName ->
            val suggested = suggestions[argumentName]?.asString()
            val expression = psiFactory.createExpression(suggested ?: "TODO()")
            val argument = psiFactory.createArgument(expression = expression, name = argumentName)
            val addedArgument = if (anchor != null) {
                element.addArgumentBefore(argument, anchor)
            } else {
                element.addArgumentWithCommentsPreserve(argument, psiFactory)
            }
            addedArgument.addNewlineBeforeIfNeeded(psiFactory, codeStyle)
            addedArgument.getArgumentExpression()
        }
    }

    private fun KtValueArgumentList.addArgumentWithCommentsPreserve(
        argument: KtValueArgument,
        psiFactory: KtPsiFactory,
    ): KtValueArgument {
        val rpar = rightParenthesis ?: return appendValueArgument(argument)
        val lastArgument = arguments.lastOrNull() ?: return appendValueArgument(argument)

        val trailingElements = generateSequence(lastArgument.nextSibling) { it.nextSibling }
            .takeWhile { it !== rpar }
            .toList()

        if (trailingElements.none { it is PsiComment }) {
            return appendValueArgument(argument)
        }

        val hasComma = trailingElements.any { it.elementType == KtTokens.COMMA }

        if (!hasComma) {
            addAfter(psiFactory.createComma(), lastArgument)
        }

        val anchor = trailingElements.lastOrNull { it !is PsiWhiteSpace } ?: lastArgument

        return addAfter(argument, anchor) as KtValueArgument
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

    @ApiStatus.Internal
    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun contextSuggestedNames(
        candidateCall: KaFunctionCall<*>,
        remainingArguments: RemainingArgumentsData,
        callExpression: KtCallExpression
    ): Map<Name, Name> {
        val alreadySuggestedNames = mutableSetOf<Name>()
        for (arg in callExpression.valueArguments) {
            (arg.getArgumentExpression() as? KtNameReferenceExpression)
                ?.getReferencedNameAsName()
                ?.let(alreadySuggestedNames::add)
        }

        val candidatesPool: List<KaContextParameterSymbol> = buildList {
            val seenNames = hashSetOf<Name>()
            for (decl in callExpression.parentsOfType<KtCallableDeclaration>()) {
                val params = when (val symbol = decl.symbol) {
                    is KaNamedFunctionSymbol, is KaPropertySymbol -> symbol.contextParameters
                    else -> emptyList()
                }
                for (p in params) {
                    if (seenNames.add(p.name)) add(p)
                }
            }
        }

        val remainingNames = remainingArguments.allContextRemainingArguments.toHashSet()
        val result = linkedMapOf<Name, Name>()

        val candidateFunction = candidateCall.symbol as? KaNamedFunctionSymbol ?: return emptyMap()
        val contextArguments = candidateCall.partiallyAppliedSymbol.contextArguments

        // shadowing context case
        val nearestContextParameterByName = candidatesPool.distinctBy { it.name }.associateBy { it.name }

        for ((index, contextParam) in candidateFunction.contextParameters.withIndex()) {
            if (contextParam.name !in remainingNames) continue

            val contextArgument = contextArguments.getOrNull(index) as? KaImplicitReceiverValue ?: continue
            if (!contextArgument.type.isSubtypeOf(contextParam.returnType)) continue

            val symbol = contextArgument.symbol as? KaContextParameterSymbol ?: continue

            val suggestion = symbol.name
            if (suggestion.isSpecial || suggestion in alreadySuggestedNames) continue

            if (nearestContextParameterByName[suggestion] != symbol) continue

            alreadySuggestedNames += suggestion
            result[contextParam.name] = suggestion
        }
        return result
    }

    /**
     * Calculates the [RemainingArgumentsData] for the call.
     * See [RemainingArgumentsData] for details.
     */
    @ApiStatus.Internal
    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    fun KaFunctionCall<*>.getRemainingArgumentsData(existingArgumentsCount: Int): RemainingArgumentsData? {
        if (!symbol.hasStableParameterNames) return null

        // if the mapping is unreliable, we don't suggest anything to avoid increasing inconsistency
        if (valueArgumentMapping.size + contextArgumentMapping.size != existingArgumentsCount) return null

        val existingValueArguments = valueArgumentMapping
            .mapNotNullTo(hashSetOf()) { arg ->
                arg.value.name.takeIf { !it.isSpecial }?.identifier
            }

        val existingContextArguments = contextArgumentMapping
            .mapNotNullTo(hashSetOf()) { arg ->
                arg.value.name.takeIf { !it.isSpecial }?.identifier
            }

        val valueRemainingArguments = symbol.valueParameters.filter { parameter ->
            !parameter.name.isSpecial && parameter.name.identifier !in existingValueArguments && !parameter.isVararg
        }

        val isExplicitContextArgumentsSupported =
            useSiteModule.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)

        val allContextParams =
            if (isExplicitContextArgumentsSupported) {
                (symbol as? KaNamedFunctionSymbol)?.contextParameters ?: emptyList()
            } else {
                emptyList()
            }

        val allContextParamNames = allContextParams
            .mapTo(hashSetOf()) { it.name }

        val contextRemainingArguments =
            if (isExplicitContextArgumentsSupported) {
                allContextParams.filter { parameter ->
                    !parameter.name.isSpecial && parameter.name.identifier !in existingContextArguments
                }
            } else {
                emptyList()
            }

        if (valueRemainingArguments.isEmpty() && contextRemainingArguments.isEmpty()) return null

        return RemainingArgumentsData(
            valueRemainingArguments.filter { !it.hasDeclaredDefaultValue }.map { it.name },
            valueRemainingArguments.map { it.name },
            contextRemainingArguments.map { it.name },
            allContextParamNames
        )
    }

    /**
     * Given the list of [allCalls] that are possible, this function returns the [RemainingArgumentsData] from the
     * overload with the fewest required (not default) value parameters.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.getRemainingArgumentsData(allCalls: List<KaCallCandidate>, existingArgumentsCount: Int): RemainingArgumentsData? {
        val allFunctionCalls = allCalls.map { info ->
            // If any of the calls cannot be resolved, we do not want to continue
            info.candidate as? KaFunctionCall<*> ?: return null
        }
        val validPossibleCalls = allFunctionCalls.filter { it.symbol.hasStableParameterNames && isValidArgumentMapping(it.valueArgumentMapping) }
        if (validPossibleCalls.isEmpty()) return null

        return validPossibleCalls
            .minBy { call -> call.symbol.valueParameters.count { !it.hasDeclaredDefaultValue } }
            .getRemainingArgumentsData(existingArgumentsCount)
    }

    /**
     * Calculates the [RemainingArgumentsData] for the [element].
     */
    @OptIn(KaExperimentalApi::class, KtExperimentalApi::class)
    fun KaSession.findRemainingNamedArguments(element: KtValueArgumentList): RemainingArgumentsData? {
        val functionCall = element.parent as? KtCallExpression ?: return null
        val resolvedCall = functionCall.resolveCall()
        val existingArgumentsCount = functionCall.valueArguments.size

        return if (resolvedCall != null) {
            // If we can unambiguously resolve the call, we get the data for it to avoid resolving all the candidates
            resolvedCall.getRemainingArgumentsData(existingArgumentsCount) ?: return null
        } else {
            getRemainingArgumentsData(functionCall.collectCallCandidates(), existingArgumentsCount)
        }
    }

    /**
     * Adds the list of the [remainingValueArguments] and [remainingContextArguments] to the [element] by passing it by name
     * with a placeholder template for each added argument.
     * Value arguments are inserted before any existing context arguments, and context arguments are appended at the end.
     */
    fun applyFix(
        project: Project,
        element: KtValueArgumentList,
        remainingValueArguments: List<Name>,
        remainingContextArguments: List<Name>,
        allContextParameterNames: Set<Name>,
        updater: ModPsiUpdater,
        nameSuggestions: Map<Name, Name> = emptyMap()
    ) {
        val psiFactory = KtPsiFactory(project, markGenerated = true)
        val codeStyle = CodeStyle.getSettings(project).kotlinCommonSettings

        val existingArguments = element.arguments.toList()

        val firstContextArg = existingArguments.firstOrNull { arg ->
            arg.getArgumentName()?.asName in allContextParameterNames
        }

        val templateFields =
            addArguments(remainingValueArguments, element, firstContextArg, psiFactory, codeStyle) +
            addArguments(remainingContextArguments, element, anchor = null, psiFactory, codeStyle, nameSuggestions)

        // Add newlines before existing arguments if we added any new arguments
        if (remainingValueArguments.isNotEmpty() || remainingContextArguments.isNotEmpty()) {
            existingArguments.forEach { it.addNewlineBeforeIfNeeded(psiFactory, codeStyle) }
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