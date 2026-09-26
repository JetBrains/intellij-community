// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.groovy

import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString

internal fun GrExpression.extractGroovyGradlePropertyKey(): String? = when (this) {
    // Any supported expression wrapped in parentheses
    is GrParenthesizedExpression ->
        operand?.extractGroovyGradlePropertyKey()

    // kotlinVersion
    is GrReferenceExpression ->
        takeIf { qualifierExpression == null }?.referenceName

    // "$kotlinVersion", "${kotlinVersion}"
    is GrString ->
        extractGroovyStringInterpolationReference()

    // providers.gradleProperty("kotlinVersion")
    // providers.gradleProperty("kotlinVersion").get()
    is GrMethodCall ->
        extractGroovyGradlePropertyCall()

    else ->
        null
}

private fun GrString.extractGroovyStringInterpolationReference(): String? {
    val injection = injections.singleOrNull() ?: return null

    if (contents.any { it.text.isNotEmpty() }) return null

    val injectedExpression = injection.expression
        ?: (injection.closableBlock?.statements?.singleOrNull() as? GrExpression)
        ?: return null

    val reference = injectedExpression as? GrReferenceExpression ?: return null
    if (reference.qualifierExpression != null) return null

    return reference.referenceName
}

private fun GrMethodCall.extractGroovyGradlePropertyCall(): String? {
    val invokedReference = invokedExpression as? GrReferenceExpression ?: return null

    return when (invokedReference.referenceName) {
        "get" -> {
            val qualifierCall =
                invokedReference.qualifierExpression as? GrMethodCall ?: return null

            qualifierCall.extractGradlePropertyProviderCall()
        }

        "gradleProperty" ->
            extractGradlePropertyProviderCall()

        else ->
            null
    }
}

private fun GrMethodCall.extractGradlePropertyProviderCall(): String? {
    val invokedReference = invokedExpression as? GrReferenceExpression ?: return null
    if (invokedReference.referenceName != "gradleProperty") return null

    val providersReference =
        invokedReference.qualifierExpression as? GrReferenceExpression ?: return null

    if (providersReference.qualifierExpression != null) return null
    if (providersReference.referenceName != "providers") return null

    return expressionArguments
        .singleOrNull()
        ?.stringLiteralValue()
}

private fun GrExpression.stringLiteralValue(): String? =
    (this as? GrLiteral)?.value as? String