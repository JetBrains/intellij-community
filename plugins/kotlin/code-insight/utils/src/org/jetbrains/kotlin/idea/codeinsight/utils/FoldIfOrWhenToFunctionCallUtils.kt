// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.addArgumentName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

object FoldIfOrWhenToFunctionCallUtils {
    data class Context(val targetArgumentIndex: Int, val parameterNames: List<Name>)

    fun branches(expression: KtExpression): List<KtExpression>? {
        val branches = when (expression) {
            is KtIfExpression -> expression.branches
            is KtWhenExpression -> expression.entries.map { it.expression }
            else -> emptyList()
        }
        val branchesSize = branches.size
        if (branchesSize < 2) return null
        return branches.filterNotNull().takeIf { it.size == branchesSize }
    }

    fun canFoldByPsi(element: KtExpression): Boolean {
        val callExpressions = element.callExpressionsFromAllBranches() ?: return false
        return differentArgumentIndex(callExpressions) != null
    }

    context(_: KaSession)
    fun getFoldingContext(element: KtExpression): Context? {
        val callExpressions = element.callExpressionsFromAllBranches() ?: return null
        val differentArgumentIndex = differentArgumentIndex(callExpressions) ?: return null

        val headCall = callExpressions.first()
        val tailCalls = callExpressions.drop(1)
        val (headFunctionFqName, headFunctionParameters) = headCall.fqNameAndParameters() ?: return null
        val headFunctionParameterSize = headFunctionParameters.size

        val sameFunctions = tailCalls.all { call ->
            val (fqName, parameters) = call.fqNameAndParameters() ?: return@all false
            fqName == headFunctionFqName &&
                    parameters.size == headFunctionParameterSize &&
                    parameters.zip(headFunctionParameters).all { it.first.name == it.second.name }
        }
        if (!sameFunctions) return null

        return Context(
            targetArgumentIndex = differentArgumentIndex,
            parameterNames = headFunctionParameters.map { it.name }
        )
    }

    fun fold(element: KtExpression, context: Context) {
        val callExpressions = element.callExpressionsFromAllBranches() ?: return
        val headCall = callExpressions.first()
        val hasNamedArgument = callExpressions.any { call -> call.valueArguments.any { it.getArgumentName() != null } }

        val argumentIndex = context.targetArgumentIndex

        val copied = element.copy() as KtExpression
        branches(copied).orEmpty().forEach { branch ->
            val call = branch.callExpression() ?: return
            val argument = call.valueArguments[argumentIndex].getArgumentExpression() ?: return
            call.getQualifiedExpressionForSelectorOrThis().replace(argument)
        }
        headCall.valueArguments[argumentIndex].getArgumentExpression()?.replace(copied)
        if (hasNamedArgument) {
            headCall.valueArguments.forEachIndexed { index, arg ->
                if (arg.getArgumentName() == null) {
                    addArgumentName(arg, context.parameterNames[index])
                }
            }
        }
        element.replace(headCall.getQualifiedExpressionForSelectorOrThis()).reformatted()
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun KtCallExpression.fqNameAndParameters(): Pair<FqName, List<KaVariableSignature<KaValueParameterSymbol>>>? {
        val functionCall = resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val fqName = functionCall.symbol.callableId?.asSingleFqName() ?: return null
        val parameters = valueArguments.mapNotNull { functionCall.argumentMapping[it.getArgumentExpression()] }
        return fqName to parameters
    }

    private fun differentArgumentIndex(callExpressions: List<KtCallExpression>): Int? {
        val headCall = callExpressions.first()
        val headCalleeText = headCall.calleeText()
        val tailCalls = callExpressions.drop(1)

        if (headCall.valueArguments.any { it is KtLambdaArgument }) return null
        val headArguments = headCall.valueArguments.mapNotNull { it.getArgumentExpression()?.text }
        val headArgumentsSize = headArguments.size
        if (headArgumentsSize != headCall.valueArguments.size) return null
        val differentArgumentIndexes = tailCalls.mapNotNull { call ->
            if (call.calleeText() != headCalleeText) return@mapNotNull null
            val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression()?.text }
            if (arguments.size != headArgumentsSize) return@mapNotNull null
            val differentArgumentIndexes = arguments.zip(headArguments).mapIndexedNotNull { index, (arg, headArg) ->
                if (arg != headArg) index else null
            }
            differentArgumentIndexes.singleOrNull()
        }
        if (differentArgumentIndexes.size != tailCalls.size || differentArgumentIndexes.distinct().size != 1) return null

        return differentArgumentIndexes.first()
    }

    private fun KtExpression.callExpressionsFromAllBranches(): List<KtCallExpression>? {
        val branches = branches(this) ?: return null
        val callExpressions = branches.mapNotNull { it.callExpression() }
        if (branches.size != callExpressions.size) return null
        return callExpressions
    }

    private fun KtExpression.callExpression(): KtCallExpression? {
        val expression = if (this is KtBlockExpression) statements.singleOrNull() else this
        return when (expression) {
            is KtCallExpression -> expression
            is KtQualifiedExpression -> expression.callExpression
            else -> null
        }?.takeIf { it.calleeExpression != null }
    }

    private fun KtCallExpression.calleeText(): String {
        val parent = this.parent
        val (receiver, op) = if (parent is KtQualifiedExpression) {
            parent.receiverExpression.text to parent.operationSign.value
        } else {
            "" to ""
        }
        return "$receiver$op${calleeExpression?.text.orEmpty()}"
    }
}
