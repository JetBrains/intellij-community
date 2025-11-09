// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendants
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_DEPENDENCY
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.kotlin.KotlinStringTemplateUPolyadicExpression
import org.jetbrains.uast.toUElementOfType

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

        if (returnType == FqName(GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
            || returnType == FqName(GRADLE_API_ARTIFACTS_DEPENDENCY)
        ) {
            when (symbol.valueParameters.firstOrNull()?.name?.identifier) {
                "group" -> return DependencyType.NAMED_ARGUMENTS
                "dependencyNotation" -> return DependencyType.SINGLE_ARGUMENT
                else -> return DependencyType.OTHER
            }
        } else if (symbol.callableId?.callableName?.asString() == "invoke" && returnType == FqName("kotlin.Unit")) {
            // customConf(libs.version.catalog.library) case
            if (symbol.valueParameters.firstOrNull()?.name?.identifier == "dependency") return DependencyType.SINGLE_ARGUMENT
            else DependencyType.OTHER
        }
        return null
    }
}

/**
 * Find an argument expression by its parameter name and index.
 * Works with any legal mix/order of named and positional arguments since positional arguments have a strict order.
 */
internal fun findNamedOrPositionalArgument(element: KtValueArgumentList, parameterName: String, expectedIndex: Int): KtExpression? {
    val argument = element.arguments.find {
        it.getArgumentName()?.asName?.identifier == parameterName
    } ?: element.arguments.getOrNull(expectedIndex).takeIf { it?.isNamed() == false }
    return argument?.getArgumentExpression()
}

internal fun KtFile.findScriptInitializers(startsWith: String): Sequence<KtScriptInitializer> =
    this.descendants(false) { it !is KtScriptInitializer }.filterIsInstance<KtScriptInitializer>().filter { it.text.startsWith(startsWith) }

internal fun KtFile.findScriptInitializer(startsWith: String): KtScriptInitializer? =
    this.findScriptInitializers(startsWith).firstOrNull()

internal fun KtScriptInitializer.getBlock(): KtBlockExpression? =
    PsiTreeUtil.findChildOfType(this, KtCallExpression::class.java)?.getBlock()

internal fun KtCallExpression.getBlock(): KtBlockExpression? =
    (valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression
        ?: lambdaArguments.lastOrNull()?.getLambdaExpression()?.bodyExpression

internal fun KtExpression.evaluateString(): String? {
    val uExpression = this.toUElementOfType<UExpression>() ?: return null
    val string = uExpression.evaluateString()
    if (string != null) return string

    val parts = (uExpression as? KotlinStringTemplateUPolyadicExpression)?.operands?.map { it.evaluateString() } ?: return null
    return if (parts.any { it == null }) null
    else parts.joinToString("")
}