// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase
import org.jetbrains.kotlin.idea.intentions.isBooleanExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isDouble
import org.jetbrains.kotlin.types.typeUtil.isFloat

class RedundantIfInspection : RedundantIfInspectionBase() {
    override fun isBooleanExpression(expression: KtExpression): Boolean = expression.isBooleanExpression()

    override fun invertEmptinessCheck(condition: KtExpression): KtExpression? {
        return EmptinessCheckFunctionUtils.invertFunctionCall(condition) {
            val context = condition.analyze(BodyResolveMode.PARTIAL)
            condition.getResolvedCall(context)?.resultingDescriptor?.fqNameSafe
        }
    }

    override fun KtIfExpression.hasConditionWithFloatingPointType(): Boolean {
        val condition = inequalityCondition() ?: return false
        val context = analyze(BodyResolveMode.PARTIAL)
        return condition.left.isFloatingPointType(context) || condition.right.isFloatingPointType(context)
    }

    private fun KtExpression?.isFloatingPointType(context: BindingContext): Boolean {
        val type = this?.getType(context) ?: return false
        return type.isFloat() || type.isDouble()
    }
}
