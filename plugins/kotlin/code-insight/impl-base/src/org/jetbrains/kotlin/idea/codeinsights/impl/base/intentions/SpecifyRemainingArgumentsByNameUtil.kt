// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.application.options.CodeStyle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.formatter.KotlinCommonCodeStyleSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.util.isLineBreak
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

object SpecifyRemainingArgumentsByNameUtil {
    class RemainingNamedArgumentData(val name: Name, val hasDefault: Boolean)

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

    fun applyFix(
        project: Project,
        element: KtValueArgumentList,
        remainingArguments: List<RemainingNamedArgumentData>,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(project, markGenerated = true)
        val templateFields = mutableListOf<KtExpression>()

        val codeStyle = CodeStyle.getSettings(project).kotlinCommonSettings

        for (remainingArgument in remainingArguments) {

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

    fun KaSession.findRemainingNamedArguments(element: KtValueArgumentList): List<RemainingNamedArgumentData>? {
        val functionCall = element.parent as? KtCallExpression ?: return null
        val resolvedCall = functionCall.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val functionSymbol = resolvedCall.partiallyAppliedSymbol.symbol
        // Do not show the intention for Java/JS/etc. sources that do not support named arguments
        if (!functionSymbol.hasStableParameterNames) return null

        val specifiedArguments = resolvedCall.argumentMapping.mapNotNull {
            it.value.name.takeIf { !it.isSpecial }?.identifier
        }.toSet()
        val remainingArguments = functionSymbol.valueParameters.filter { parameter ->
            !parameter.name.isSpecial && parameter.name.identifier !in specifiedArguments && !parameter.isVararg
        }.map { parameter ->
            RemainingNamedArgumentData(parameter.name, parameter.hasDefaultValue)
        }

        return remainingArguments.takeIf { it.isNotEmpty() }
    }
}