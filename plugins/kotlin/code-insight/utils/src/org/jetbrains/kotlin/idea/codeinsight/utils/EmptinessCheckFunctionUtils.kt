// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

object EmptinessCheckFunctionUtils {
    context(KtAnalysisSession)
    fun invertFunctionCall(expression: KtExpression): KtExpression? {
        return invertFunctionCall(expression) {
            val symbol = it.resolveCall()?.successfulCallOrNull<KtCallableMemberCall<*, *>>()?.symbol
            symbol?.callableIdIfNonLocal?.asSingleFqName()
        }
    }

    fun invertFunctionCall(expression: KtExpression, functionFqName: (KtCallExpression) -> FqName?): KtExpression? {
        val psiFactory = KtPsiFactory(expression.project)
        val inverted = when (expression) {
            is KtCallExpression -> {
                val inverted = invertedFunctionName(expression, functionFqName) ?: return null
                psiFactory.createExpression("$inverted()")
            }

            is KtQualifiedExpression -> {
                val call = expression.callExpression ?: return null
                val inverted = invertedFunctionName(call, functionFqName) ?: return null
                psiFactory.createExpressionByPattern("$0.$inverted()", expression.receiverExpression, reformat = false)
            }

            else -> return null
        }
        return inverted
    }

    private fun invertedFunctionName(callExpression: KtCallExpression, functionFqName: (KtCallExpression) -> FqName?): String? {
        val fromFunctionName = callExpression.calleeExpression?.text ?: return null
        val (fromFunctionFqNames, toFunctionName) = functionNames[fromFunctionName] ?: return null
        if (functionFqName(callExpression) !in fromFunctionFqNames) return null
        return toFunctionName
    }

    private val packages: List<String> = listOf(
        "java.util.ArrayList",
        "java.util.HashMap",
        "java.util.HashSet",
        "java.util.LinkedHashMap",
        "java.util.LinkedHashSet",
        "kotlin.collections",
        "kotlin.collections.List",
        "kotlin.collections.Set",
        "kotlin.collections.Map",
        "kotlin.collections.MutableList",
        "kotlin.collections.MutableSet",
        "kotlin.collections.MutableMap",
        "kotlin.text"
    )

    private val functionNames: Map<String, Pair<List<FqName>, String>> = mapOf(
        "isEmpty" to Pair(packages.map { FqName("$it.isEmpty") }, "isNotEmpty"),
        "isNotEmpty" to Pair(packages.map { FqName("$it.isNotEmpty") }, "isEmpty"),
        "isBlank" to Pair(listOf(FqName("kotlin.text.isBlank")), "isNotBlank"),
        "isNotBlank" to Pair(listOf(FqName("kotlin.text.isNotBlank")), "isBlank"),
    )
}
