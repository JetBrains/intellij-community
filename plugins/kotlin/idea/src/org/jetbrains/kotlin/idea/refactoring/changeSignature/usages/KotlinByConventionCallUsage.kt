// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.usages

import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.removeEmptyArgumentListIfApplicable
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.inspections.conventionNameCalls.ReplaceGetOrSetInspection
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.ReplaceInvokeIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.util.OperatorNameConventions

class KotlinByConventionCallUsage(
    expression: KtExpression,
    private val callee: KotlinCallableDefinitionUsage<*>
) : KotlinUsageInfo<KtExpression>(expression) {
    private var resolvedCall: ResolvedCall<*>? = null
    private var convertedCallExpression: KtCallExpression? = null

    private fun foldExpression(expression: KtDotQualifiedExpression, changeInfo: ChangeInfo) {
        when (changeInfo.newName) {
            OperatorNameConventions.INVOKE.asString() -> {
                with(ReplaceInvokeIntention()) {
                    if (applicabilityRange(expression) != null) {
                        OperatorToFunctionConverter.replaceExplicitInvokeCallWithImplicit(expression)
                            ?.getPossiblyQualifiedCallExpression()
                            ?.valueArgumentList?.let(::removeEmptyArgumentListIfApplicable)
                    }
                }
            }

            OperatorNameConventions.GET.asString() -> {
                with(ReplaceGetOrSetInspection()) {
                    if (isApplicable(expression)) {
                        applyTo(expression)
                    }
                }
            }
        }
    }

    override fun getElement(): KtExpression? {
        return convertedCallExpression ?: super.getElement()
    }

    override fun preprocessUsage() {
        val element = element ?: return
        val convertedExpression = OperatorToFunctionConverter.convert(element).first
        val callExpression = convertedExpression.getPossiblyQualifiedCallExpression() ?: return
        resolvedCall = callExpression.resolveToCall()
        convertedCallExpression = callExpression
    }

    override fun processUsage(changeInfo: KotlinChangeInfo, element: KtExpression, allUsages: Array<out UsageInfo>): Boolean {
        val resolvedCall = resolvedCall ?: return true
        val callExpression = convertedCallExpression ?: return true
        val callProcessor = KotlinFunctionCallUsage(callExpression, callee, resolvedCall)
        val newExpression = callProcessor.processUsageAndGetResult(
            changeInfo = changeInfo,
            element = callExpression,
            allUsages = allUsages,
            skipRedundantArgumentList = true,
        ) as? KtExpression

        val qualifiedCall = newExpression?.getQualifiedExpressionForSelectorOrThis() as? KtDotQualifiedExpression
        if (qualifiedCall != null) {
            foldExpression(qualifiedCall, changeInfo)
        }

        return true
    }
}