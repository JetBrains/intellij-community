// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_DEPENDENCY
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY

internal const val KOTLIN_GROUP_ID: String = "org.jetbrains.kotlin"
internal const val GRADLE_KOTLIN_PACKAGE: String = "org.gradle.kotlin.dsl"

internal enum class DependencyType {
    SINGLE_ARGUMENT, NAMED_ARGUMENTS, OTHER
}

/**
 * @return dependency argument type or null if the expression is not a dependency call
 */
internal fun findDependencyType(expression: KtCallExpression): DependencyType? {
    analyze(expression) {
        val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null
        if (symbol.callableId?.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return null
        val returnType = symbol.returnType.symbol?.classId?.asSingleFqName() ?: return null
        if (returnType != FqName(GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
            && returnType != FqName(GRADLE_API_ARTIFACTS_DEPENDENCY)
        ) return null

        val parameters = symbol.valueParameters
        if (parameters.isEmpty()) return DependencyType.OTHER
        val firstParameter = parameters.first()
        when (firstParameter.name.identifier) {
            "group" -> return DependencyType.NAMED_ARGUMENTS
            "dependencyNotation" -> return DependencyType.SINGLE_ARGUMENT
            else -> return DependencyType.OTHER
        }
    }
}

/**
 * Find an argument expression by its parameter name and index.
 * Works with any legal mix/order of named and positional arguments since positional arguments have a strict order.
 */
internal fun findNamedOrPositionalArgument(element: KtValueArgumentList, parameterName: String, expectedIndex: Int): KtExpression? {
    val argument = element.arguments.find {
        it.getArgumentName()?.asName?.identifier == parameterName
    } ?: element.arguments.getOrNull(expectedIndex)
    return argument?.getArgumentExpression()
}