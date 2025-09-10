// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.refactoring.appendCallOrQualifiedExpression
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateParameterList
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.unpackFunctionLiteral

internal class ConvertToIndexedFunctionCallIntention :
    KotlinApplicableModCommandAction<KtCallExpression, ConvertToIndexedFunctionCallIntention.Context>(KtCallExpression::class) {

    data class Context(
        val functionName: String,
        val newFunctionName: String,
        val functionLiteral: KtFunctionLiteral
    )

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.indexed.function.call")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val calleeText = element.calleeExpression?.text ?: return false
        return functions.containsKey(calleeText)
    }

    private fun KtCallExpression.firstLambdaExpression(): KtLambdaExpression? {
        // Trailing lambda: forEach { s -> println(s) }
        lambdaArguments.firstOrNull()?.getLambdaExpression()?.let { return it }

        // Parenthesized lambda: forEach({ s -> println(s) })
        return valueArguments
            .asSequence()
            .mapNotNull { it.getArgumentExpression()?.unpackFunctionLiteral() }
            .firstOrNull()
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val callee = element.calleeExpression ?: return null
        val (functionFqName, newFunctionName) = functions[callee.text] ?: return null

        val resolvedFqName = element.resolveToCall()
            ?.successfulFunctionCallOrNull()
            ?.symbol
            ?.callableId
            ?.asSingleFqName() ?: return null
        if (resolvedFqName != functionFqName) return null

        val functionLiteral = element.firstLambdaExpression()?.functionLiteral ?: return null
        return Context(callee.text, newFunctionName, functionLiteral)
    }

    override fun getPresentation(
        context: ActionContext,
        element: KtCallExpression,
    ): Presentation? {
        val callee = element.calleeExpression ?: return null
        val (_, newFunctionName) = functions[callee.text] ?: return null
        val text = KotlinBundle.message("convert.to.0", "'${newFunctionName}'")
        return Presentation.of(text)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val (functionName, newFunctionName, functionLiteral) = elementContext
        val psiFactory = KtPsiFactory(element.project)

        updateLabeledReturns(functionLiteral, functionName, newFunctionName, psiFactory)
        addIndexParameter(functionLiteral, psiFactory)
        replaceCallWithIndexedVariant(element, newFunctionName, psiFactory)
    }

    private fun updateLabeledReturns(
        functionLiteral: KtFunctionLiteral,
        oldFunctionName: String,
        newFunctionName: String,
        psiFactory: KtPsiFactory
    ) {
        val labeledReturns = functionLiteral.collectDescendantsOfType<KtReturnExpression> {
            it.getLabelName() == oldFunctionName
        }

        labeledReturns.forEach { returnExpr ->
            val returnedExpression = returnExpr.returnedExpression
            val newLabeledReturn = if (returnedExpression != null) {
                psiFactory.createExpressionByPattern("return@$newFunctionName $0", returnedExpression)
            } else {
                psiFactory.createExpression("return@$newFunctionName")
            }
            returnExpr.replace(newLabeledReturn)
        }
    }

    private fun addIndexParameter(functionLiteral: KtFunctionLiteral, psiFactory: KtPsiFactory) {
        val parameterList = functionLiteral.getOrCreateParameterList()
        val parameters = parameterList.parameters

        val nameValidator = KotlinNameValidatorProvider.getInstance().createNameValidator(
            container = functionLiteral,
            target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
            anchor = functionLiteral
        )
        val indexParameterName = KotlinNameSuggester.suggestNameByName("index", nameValidator)
        val indexParameter = psiFactory.createParameter(indexParameterName)

        if (parameters.isEmpty()) {
            parameterList.addParameter(indexParameter)
            val implicitParam = psiFactory.createParameter(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)
            parameterList.addParameter(implicitParam)
        } else {
            parameterList.addParameterBefore(indexParameter, parameters.first())
        }
    }

    private fun replaceCallWithIndexedVariant(
        element: KtCallExpression,
        newFunctionName: String,
        psiFactory: KtPsiFactory
    ) {
        val callOrQualified = element.getQualifiedExpressionForSelector() ?: element
        val result = callOrQualified.replace(
            psiFactory.buildExpression {
                appendCallOrQualifiedExpression(element, newFunctionName)
            }
        )
        CodeStyleManager.getInstance(element.project).reformat(result)
    }
}

private const val indexed: String = "Indexed"

private val functions: Map<String, Pair<FqName, String>> = listOf(
    Pair("filter", "filter$indexed"),
    Pair("filterTo", "filter${indexed}To"),
    Pair("fold", "fold$indexed"),
    Pair("foldRight", "foldRight$indexed"),
    Pair("forEach", "forEach$indexed"),
    Pair("map", "map$indexed"),
    Pair("mapNotNull", "map${indexed}NotNull"),
    Pair("mapNotNullTo", "map${indexed}NotNullTo"),
    Pair("mapTo", "map${indexed}To"),
    Pair("onEach", "onEach$indexed"),
    Pair("reduce", "reduce$indexed"),
    Pair("reduceOrNull", "reduce${indexed}OrNull"),
    Pair("reduceRight", "reduceRight$indexed"),
    Pair("reduceRightOrNull", "reduceRight${indexed}OrNull"),
    Pair("runningFold", "runningFold$indexed"),
    Pair("runningReduce", "runningReduce$indexed"),
    Pair("scan", "scan$indexed"),
    Pair("scanReduce", "scanReduce$indexed"),
).associate { (functionName, indexedFunctionName) ->
    functionName to (FqName("kotlin.collections.$functionName") to indexedFunctionName)
}
