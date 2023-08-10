// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression

internal class RedundantIfInspection : RedundantIfInspectionBase() {
    override fun isBooleanExpression(expression: KtExpression): Boolean = analyze(expression) {
        expression.getKtType()?.isBoolean == true
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun invertEmptinessCheck(condition: KtExpression): KtExpression? =
        allowAnalysisOnEdt {
            analyze(condition) {
                EmptinessCheckFunctionUtils.invertFunctionCall(condition)
            }
        }

    override fun KtIfExpression.hasConditionWithFloatingPointType(): Boolean {
        val condition = inequalityCondition() ?: return false
        return analyze(this) {
            condition.left.isFloatingPointType() || condition.right.isFloatingPointType()
        }
    }

    context(KtAnalysisSession)
    private fun KtExpression?.isFloatingPointType(): Boolean {
        val type = this?.getKtType() ?: return false
        return type.isFloat || type.isDouble
    }
}
