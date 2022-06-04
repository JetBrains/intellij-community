// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

abstract class AbstractUselessCallInspection : AbstractKotlinInspection() {

    protected abstract val uselessFqNames: Map<String, Conversion>

    protected abstract val uselessNames: Set<String>

    protected abstract fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        context: BindingContext,
        conversion: Conversion
    )

    inner class QualifiedExpressionVisitor internal constructor(val holder: ProblemsHolder, val isOnTheFly: Boolean) : KtVisitorVoid() {
        override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            super.visitQualifiedExpression(expression)
            val selector = expression.selectorExpression as? KtCallExpression ?: return
            val calleeExpression = selector.calleeExpression ?: return
            if (calleeExpression.text !in uselessNames) return

            val context = expression.analyze()
            val resolvedCall = expression.getResolvedCall(context) ?: return
            val conversion = uselessFqNames[resolvedCall.resultingDescriptor.fqNameOrNull()?.asString()] ?: return

            suggestConversionIfNeeded(expression, calleeExpression, context, conversion)
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = QualifiedExpressionVisitor(holder, isOnTheFly)

    protected data class Conversion(val replacementName: String? = null)

    protected companion object {

        val deleteConversion = Conversion()

        fun Set<String>.toShortNames() = mapTo(mutableSetOf()) { fqName -> fqName.takeLastWhile { it != '.' } }
    }
}