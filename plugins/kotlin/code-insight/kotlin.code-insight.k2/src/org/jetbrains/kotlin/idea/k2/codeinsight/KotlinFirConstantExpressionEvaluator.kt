// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiConstantEvaluationHelper.AuxEvaluator
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.ConstantExpressionEvaluator
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtExpression

class KotlinFirConstantExpressionEvaluator : ConstantExpressionEvaluator {
    override fun computeConstantExpression(
        expression: PsiElement,
        throwExceptionOnOverflow: Boolean
    ): Any? {
        return computeExpression(expression, throwExceptionOnOverflow, null)
    }

    override fun computeExpression(
        expression: PsiElement,
        throwExceptionOnOverflow: Boolean,
        auxEvaluator: AuxEvaluator?
    ): Any? {
        val ktExpression = expression.unwrapped as? KtExpression ?: return null
        val analyze = {
            analyze(ktExpression) {
                ktExpression.evaluate()
                    ?.takeUnless { it is KaConstantValue.ErrorValue }?.value
            }
        }

        if (!Registry.`is`("kotlin.k2.allow.constant.computation.on.EDT")) {
            return analyze()
        }

        @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
        return allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze()
            }
        }
    }
}