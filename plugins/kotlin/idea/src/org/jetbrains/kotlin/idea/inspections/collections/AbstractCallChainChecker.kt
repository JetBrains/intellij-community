// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainExpressions
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConversionId
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.DefaultValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

abstract class AbstractCallChainChecker : AbstractKotlinInspection() {

    protected fun findQualifiedConversion(
        callChainExpressions: CallChainExpressions,
        conversionGroups: Map<ConversionId, List<CallChainConversion>>,
        additionalCallCheck: (CallChainConversion, ResolvedCall<*>, ResolvedCall<*>, BindingContext) -> Boolean
    ): CallChainConversion? {
        val apiVersion by lazy { callChainExpressions.qualifiedExpression.languageVersionSettings.apiVersion }
        val actualConversions = conversionGroups[ConversionId(
            callChainExpressions.firstCalleeExpression,
            callChainExpressions.secondCalleeExpression,
        )]?.filter {
            val replaceableApiVersion = it.replaceableApiVersion
            replaceableApiVersion == null || apiVersion >= replaceableApiVersion
        }?.sortedByDescending { it.removeNotNullAssertion } ?: return null

        val context = callChainExpressions.secondExpression.analyze()
        val firstResolvedCall = callChainExpressions.firstExpression.getResolvedCall(context) ?: return null
        val secondResolvedCall = callChainExpressions.secondExpression.getResolvedCall(context) ?: return null
        val conversion = actualConversions.firstOrNull {
            firstResolvedCall.isCalling(it.firstFqName) && additionalCallCheck(it, firstResolvedCall, secondResolvedCall, context)
        } ?: return null

        // Do not apply for lambdas with return inside
        val lambdaArgument = callChainExpressions.firstCallExpression.lambdaArguments.firstOrNull()
        if (lambdaArgument?.anyDescendantOfType<KtReturnExpression>() == true) return null

        if (!secondResolvedCall.isCalling(conversion.secondFqName)) return null
        if (secondResolvedCall.valueArguments.any { (parameter, resolvedArgument) ->
                parameter.type.isFunctionOfAnyKind() &&
                        resolvedArgument !is DefaultValueArgument
            }
        ) return null

        return conversion
    }
}
