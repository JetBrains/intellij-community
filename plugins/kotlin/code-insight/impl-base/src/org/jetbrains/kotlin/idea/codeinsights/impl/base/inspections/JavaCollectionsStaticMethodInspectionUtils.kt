// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.openapi.module.ModuleUtilCore
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument

object JavaCollectionsStaticMethodInspectionUtils {

    context(_: KaSession)
    fun getMethodIfItsArgumentIsMutableList(expression: KtDotQualifiedExpression): Pair<String, KtValueArgument>? =
        getMethodIfCanReplaceItWithStdlib(expression) { methodArgument ->
            isMutableListOrSubtype(methodArgument)
        }

    context(_: KaSession)
    fun getMethodIfItsArgumentIsImmutableList(expression: KtDotQualifiedExpression): Pair<String, KtValueArgument>? =
        getMethodIfCanReplaceItWithStdlib(expression) { methodArgument ->
            isListOrSubtype(methodArgument) && !isMutableListOrSubtype(
                methodArgument
            )
        }

    context(_: KaSession)
    private fun getMethodIfCanReplaceItWithStdlib(
        expression: KtDotQualifiedExpression,
        isValidFirstArgument: (KaType?) -> Boolean
    ): Pair<String, KtValueArgument>? {
        val callExpression = expression.callExpression ?: return null
        val args = callExpression.valueArguments
        val firstArg = args.firstOrNull() ?: return null
        val firstArgType = firstArg.getArgumentExpression()?.expressionType
        if (!isValidFirstArgument(firstArgType)) return null

        val call = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val callableId = call.partiallyAppliedSymbol.symbol.callableId ?: return null
        val fqName = callableId.asSingleFqName().asString()

        if (!canReplaceWithStdLib(expression, fqName, args)) return null

        val methodName = fqName.split(".").last()
        return methodName to firstArg
    }

    private fun checkApiVersion(expression: KtDotQualifiedExpression): Boolean {
        val module = ModuleUtilCore.findModuleForPsiElement(expression) ?: return true
        return module.languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_1_2
    }

    private fun canReplaceWithStdLib(expression: KtDotQualifiedExpression, fqName: String, args: List<KtValueArgument>): Boolean {
        if (!fqName.startsWith("java.util.Collections.")) return false
        val size = args.size
        return when (fqName) {
            "java.util.Collections.fill" -> checkApiVersion(expression) && size == 2
            "java.util.Collections.reverse" -> size == 1
            "java.util.Collections.shuffle" -> checkApiVersion(expression) && (size == 1 || size == 2)
            "java.util.Collections.sort" -> {
                size == 1 || (size == 2 && args.getOrNull(1)?.getArgumentExpression() is KtLambdaExpression)
            }

            else -> false
        }
    }

    context(_: KaSession)
    private fun isMutableListOrSubtype(type: KaType?): Boolean {
        return type?.isSubtypeOf(StandardClassIds.MutableList) == true
    }

    context(_: KaSession)
    private fun isListOrSubtype(type: KaType?): Boolean {
        return type?.isSubtypeOf(StandardClassIds.List) == true
    }
}