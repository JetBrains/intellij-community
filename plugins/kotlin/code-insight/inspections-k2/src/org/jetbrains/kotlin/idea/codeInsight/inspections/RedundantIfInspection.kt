// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression

internal class RedundantIfInspection : RedundantIfInspectionBase() {
    override fun isBooleanExpression(expression: KtExpression): Boolean = analyze(expression) {
        expression.expressionType?.classId == KaStandardTypeClassIds.BOOLEAN
    }

    override fun isNotNullableBooleanExpression(expression: KtExpression): Boolean = analyze(expression) {
        expression.expressionType?.let { it.classId == KaStandardTypeClassIds.BOOLEAN && !it.isMarkedNullable } ?: false
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun invertEmptinessCheck(condition: KtExpression): KtExpression? =
        allowAnalysisOnEdt {
            EmptinessCheckFunctionUtils.invertFunctionCall(condition)
        }

    override fun KtIfExpression.hasConditionWithFloatingPointType(): Boolean {
        val condition = inequalityCondition() ?: return false
        return analyze(this) {
            condition.left.isFloatingPointType() || condition.right.isFloatingPointType()
        }
    }

    context(_: KaSession)
    private fun KtExpression?.isFloatingPointType(): Boolean {
        val type = this?.expressionType ?: return false
        return type.classId == KaStandardTypeClassIds.FLOAT || type.classId == KaStandardTypeClassIds.DOUBLE
    }
}
