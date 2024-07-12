// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversions.getCallExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConversionId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.DefaultValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

abstract class AbstractCallChainChecker : AbstractKotlinInspection() {

    protected fun findQualifiedConversion(
        expression: KtQualifiedExpression,
        conversionGroups: Map<ConversionId, List<CallChainConversion>>,
        additionalCallCheck: (CallChainConversion, ResolvedCall<*>, ResolvedCall<*>, BindingContext) -> Boolean
    ): CallChainConversion? {
        val firstExpression = expression.receiverExpression
        val firstCallExpression = getCallExpression(firstExpression) ?: return null
        val firstCalleeExpression = firstCallExpression.calleeExpression ?: return null

        val secondCallExpression = expression.selectorExpression as? KtCallExpression ?: return null

        val secondCalleeExpression = secondCallExpression.calleeExpression ?: return null
        val apiVersion by lazy { expression.languageVersionSettings.apiVersion }
        val actualConversions = conversionGroups[ConversionId(firstCalleeExpression, secondCalleeExpression)]?.filter {
            val replaceableApiVersion = it.replaceableApiVersion
            replaceableApiVersion == null || apiVersion >= replaceableApiVersion
        }?.sortedByDescending { it.removeNotNullAssertion } ?: return null

        val context = expression.analyze()
        val firstResolvedCall = firstExpression.getResolvedCall(context) ?: return null
        val secondResolvedCall = expression.getResolvedCall(context) ?: return null
        val conversion = actualConversions.firstOrNull {
            firstResolvedCall.isCalling(it.firstFqName) && additionalCallCheck(it, firstResolvedCall, secondResolvedCall, context)
        } ?: return null

        // Do not apply for lambdas with return inside
        val lambdaArgument = firstCallExpression.lambdaArguments.firstOrNull()
        if (lambdaArgument?.anyDescendantOfType<KtReturnExpression>() == true) return null

        if (!secondResolvedCall.isCalling(conversion.secondFqName)) return null
        if (secondResolvedCall.valueArguments.any { (parameter, resolvedArgument) ->
                parameter.type.isFunctionOfAnyKind() &&
                        resolvedArgument !is DefaultValueArgument
            }
        ) return null

        return conversion
    }

    companion object {

    }
}