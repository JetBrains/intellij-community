// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.formatter.KotlinCommonCodeStyleSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.util.isLineBreak
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class RemainingNamedArgumentData(val name: Name, val hasDefault: Boolean)

internal sealed class SpecifyRemainingArgumentsByNameIntention(
    @Nls
    private val familyName: String
) : KotlinApplicableModCommandAction<KtValueArgumentList, List<RemainingNamedArgumentData>>(KtValueArgumentList::class) {
    override fun getFamilyName(): String = familyName

    /**
     * Returns whether the [argument] should be specified by this intention.
     * The intention is only shown if at least one argument satisfies this function.
     */
    internal abstract fun shouldSpecifyArgument(argument: RemainingNamedArgumentData): Boolean

    /**
     * Return true if the intention should be offered to the user.
     * This is used to hide implementations of this class if another one with the same effect is already shown.
     */
    internal abstract fun shouldOfferIntention(remainingArguments: List<RemainingNamedArgumentData>): Boolean

    override fun getApplicableRanges(element: KtValueArgumentList): List<TextRange> {
        val firstArgument = element.arguments.firstOrNull() ?: return ApplicabilityRange.self(element)
        val lastArgument = element.arguments.lastOrNull() ?: firstArgument

        // We only want the intention to show if the caret is near the start or end of the argument list
        val startTextRange = TextRange(0, firstArgument.startOffsetInParent)
        val endTextRange = TextRange(lastArgument.startOffsetInParent + lastArgument.textLength, element.textLength)

        return listOf(startTextRange, endTextRange)
    }

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

    private fun KtValueArgumentList.trimDoubleEndingNewlines(psiFactory: KtPsiFactory) {
        val rightParenthesis = rightParenthesis ?: return
        val prevWhitespace = rightParenthesis.prevSibling as? PsiWhiteSpace ?: return
        if (StringUtil.getLineBreakCount(prevWhitespace.text) > 1) {
            prevWhitespace.replace(psiFactory.createNewLine())
        }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtValueArgumentList,
        elementContext: List<RemainingNamedArgumentData>,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(actionContext.project, markGenerated = true)
        val templateFields = mutableListOf<KtExpression>()

        val codeStyle = CodeStyle.getSettings(actionContext.project).kotlinCommonSettings

        for (remainingArgument in elementContext) {
            if (!shouldSpecifyArgument(remainingArgument)) continue

            val todoExpression = psiFactory.createExpression("TODO()")
            val argument = psiFactory.createArgument(expression = todoExpression, name = remainingArgument.name)
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

    override fun getPresentation(context: ActionContext, element: KtValueArgumentList): Presentation? {
        return super.getPresentation(context, element)?.withPriority(PriorityAction.Priority.HIGH)
    }

    context(KaSession)
    override fun prepareContext(element: KtValueArgumentList): List<RemainingNamedArgumentData>? {
        val functionCall = element.parent as? KtCallExpression ?: return null
        val resolvedCall = functionCall.resolveCallOld()?.singleFunctionCallOrNull() ?: return null
        val functionSymbol = resolvedCall.partiallyAppliedSymbol.symbol
        // Do not show the intention for Java/JS/etc. sources that do not support named arguments
        if (!functionSymbol.hasStableParameterNames) return null

        val specifiedArguments = resolvedCall.argumentMapping.map { it.value.name.identifier }.toSet()
        val remainingArguments = functionSymbol.valueParameters.filter { parameter ->
            parameter.name.identifier !in specifiedArguments && !parameter.isVararg
        }.map { parameter ->
            RemainingNamedArgumentData(parameter.name, parameter.hasDefaultValue)
        }

        if (!shouldOfferIntention(remainingArguments)) return null

        return remainingArguments
            .filter { shouldSpecifyArgument(it) }
            .takeIf { it.isNotEmpty() }
    }
}

internal class SpecifyAllRemainingArgumentsByNameIntention : SpecifyRemainingArgumentsByNameIntention(
    KotlinBundle.getMessage("specify.all.remaining.arguments.by.name")
) {
    override fun shouldSpecifyArgument(argument: RemainingNamedArgumentData): Boolean = true
    override fun shouldOfferIntention(remainingArguments: List<RemainingNamedArgumentData>): Boolean = true
}

internal class SpecifyRemainingRequiredArgumentsByNameIntention : SpecifyRemainingArgumentsByNameIntention(
    KotlinBundle.getMessage("specify.remaining.required.arguments.by.name")
) {
    override fun shouldSpecifyArgument(argument: RemainingNamedArgumentData): Boolean = !argument.hasDefault

    override fun shouldOfferIntention(remainingArguments: List<RemainingNamedArgumentData>): Boolean {
        val argumentsWithDefault = remainingArguments.count { it.hasDefault }
        return argumentsWithDefault in (1..<remainingArguments.size)
    }
}