// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.DeprecationCollectingInspection
import org.jetbrains.kotlin.idea.statistics.LanguageFeatureDeprecationCollector
import org.jetbrains.kotlin.idea.statistics.NewAndDeprecatedFeaturesInspectionData
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType
import org.jetbrains.kotlin.idea.util.getRangeBinaryExpressionType
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

abstract class AbstractRangeInspection(
  collector: LanguageFeatureDeprecationCollector<NewAndDeprecatedFeaturesInspectionData>? = null,
  defaultDeprecationData: NewAndDeprecatedFeaturesInspectionData? = null
) : DeprecationCollectingInspection<NewAndDeprecatedFeaturesInspectionData>(collector, defaultDeprecationData) {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) = object : KtVisitorVoid() {
        override fun visitBinaryExpression(binaryExpression: KtBinaryExpression) {
            visitRange(binaryExpression, holder, session)
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            visitRange(expression, holder, session)
        }
    }

    private fun visitRange(expression: KtExpression, holder: ProblemsHolder, session: LocalInspectionToolSession) {
        val context = lazy { expression.analyze(BodyResolveMode.PARTIAL) }
        visitRange(expression, context, expression.getRangeBinaryExpressionType(context) ?: return, holder, session)
    }

    abstract fun visitRange(
        range: KtExpression,
        context: Lazy<BindingContext>,
        type: RangeKtExpressionType,
        holder: ProblemsHolder,
        session: LocalInspectionToolSession
    )

    companion object {
        fun KtExpression.constantValueOrNull(context: BindingContext? = null): ConstantValue<Any?>? {
            val c = context ?: this.analyze(BodyResolveMode.PARTIAL)

            val constant = ConstantExpressionEvaluator.getConstant(this, c) ?: return null

            return constant.toConstantValue(getType(c) ?: return null)
        }
    }
}
