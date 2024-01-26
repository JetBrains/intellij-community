// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid


abstract class AbstractUselessCallInspection : AbstractKotlinInspection() {
    protected abstract val uselessFqNames: Map<String, Conversion>

    protected abstract val uselessNames: Set<String>

    context(KtAnalysisSession)
    protected abstract fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        conversion: Conversion
    )

    inner class QualifiedExpressionVisitor internal constructor(val holder: ProblemsHolder, val isOnTheFly: Boolean) : KtVisitorVoid() {
        override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            super.visitQualifiedExpression(expression)
            val selector = expression.selectorExpression as? KtCallExpression ?: return
            val calleeExpression = selector.calleeExpression ?: return
            if (calleeExpression.text !in uselessNames) return

            analyze(calleeExpression) {
                val resolvedCall = calleeExpression.resolveCall()?.singleFunctionCallOrNull() ?: return
                val fqName = resolvedCall.symbol.callableIdIfNonLocal?.asSingleFqName() ?: return
                val conversion = uselessFqNames[fqName.asString()] ?: return
                suggestConversionIfNeeded(expression, calleeExpression, conversion)
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = QualifiedExpressionVisitor(holder, isOnTheFly)

    protected data class Conversion(val replacementName: String? = null)

    protected companion object {

        val deleteConversion = Conversion()

        fun Set<String>.toShortNames() = mapTo(mutableSetOf()) { fqName -> fqName.takeLastWhile { it != '.' } }

        context(KtAnalysisSession)
        fun KtQualifiedExpression.invertSelectorFunction(): KtQualifiedExpression? {
            return EmptinessCheckFunctionUtils.invertFunctionCall(this) as? KtQualifiedExpression
        }
    }
}