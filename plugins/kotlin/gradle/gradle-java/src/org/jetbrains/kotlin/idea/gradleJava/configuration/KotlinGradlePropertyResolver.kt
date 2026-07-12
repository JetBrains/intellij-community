// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal fun KtExpression.extractKotlinGradlePropertyKey(): String? {
    val expression = KtPsiUtil.deparenthesize(this) ?: return null

    return when (expression) {
        // providers.gradleProperty("kotlinPluginVersion")
        // providers.gradleProperty("kotlinPluginVersion").get()
        // extra["kotlinPluginVersion"].toString()
        is KtDotQualifiedExpression ->
            expression.extractKotlinGradlePropertyCall()

        else ->
            null
    }
}

private fun KtDotQualifiedExpression.extractKotlinGradlePropertyCall(): String? {
    val call = selectorExpression as? KtCallExpression ?: return null

    return when (call.calleeExpression?.text) {
        "gradleProperty" ->
            extractGradlePropertyProviderCall()

        "get" -> {
            if (call.valueArguments.isNotEmpty()) return null

            (receiverExpression as? KtDotQualifiedExpression)
                ?.extractGradlePropertyProviderCall()
        }

        "toString" -> {
            if (call.valueArguments.isNotEmpty()) return null

            (receiverExpression as? KtArrayAccessExpression)
                ?.extractExtraPropertyKey()
        }

        else ->
            null
    }
}

private fun KtDotQualifiedExpression.extractGradlePropertyProviderCall(): String? {
    val providersReference = receiverExpression as? KtNameReferenceExpression ?: return null
    if (providersReference.getReferencedName() != "providers") return null

    val call = selectorExpression as? KtCallExpression ?: return null
    if (call.calleeExpression?.text != "gradleProperty") return null

    return call.valueArguments
        .singleOrNull()
        ?.getArgumentExpression()
        ?.stringLiteralValue()
}

private fun KtArrayAccessExpression.extractExtraPropertyKey(): String? {
    val extraReference = arrayExpression as? KtNameReferenceExpression ?: return null
    if (extraReference.getReferencedName() != "extra") return null

    return indexExpressions
        .singleOrNull()
        ?.stringLiteralValue()
}

private fun KtExpression.stringLiteralValue(): String? {
    val expression = KtPsiUtil.deparenthesize(this) as? KtStringTemplateExpression ?: return null
    return (expression.entries.singleOrNull() as? KtLiteralStringTemplateEntry)?.text
}
