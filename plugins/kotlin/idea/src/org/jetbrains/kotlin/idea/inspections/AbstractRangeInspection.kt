// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

abstract class AbstractRangeInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitBinaryExpression(binaryExpression: KtBinaryExpression) {
            val operator = binaryExpression.operationReference.text
            visitRange(binaryExpression, operator, holder)
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            val callee = expression.callExpression?.calleeExpression?.text ?: return
            visitRange(expression, callee, holder)
        }
    }

    private fun visitRange(expression: KtExpression, operator: String, holder: ProblemsHolder) {
        if (operator !in rangeFunctions) return
        val context = expression.analyze(BodyResolveMode.PARTIAL)
        val fqName = expression.getResolvedCall(context)?.resultingDescriptor?.fqNameOrNull() ?: return
        when (fqName) {
            in rangeToFqNames -> visitRangeTo(expression, context, holder)
            untilFqName -> visitUntil(expression, context, holder)
            downToFqNames -> visitDownTo(expression, context, holder)
        }
    }

    abstract fun visitRangeTo(expression: KtExpression, context: BindingContext, holder: ProblemsHolder)

    abstract fun visitUntil(expression: KtExpression, context: BindingContext, holder: ProblemsHolder)

    abstract fun visitDownTo(expression: KtExpression, context: BindingContext, holder: ProblemsHolder)

    companion object {
        private val rangeFunctions = listOf("..", "rangeTo", "until", "downTo")

        private val rangeToFqNames = listOf(
            "Char",
            "Byte", "Short", "Int", "Long",
            "UByte", "UShort", "UInt", "ULong"
        ).map { FqName("kotlin.$it.rangeTo") }

        private val untilFqName = FqName("kotlin.ranges.until")

        private val downToFqNames = FqName("kotlin.ranges.downTo")

        fun KtExpression.constantValueOrNull(context: BindingContext? = null): ConstantValue<Any?>? {
            val c = context ?: this.analyze(BodyResolveMode.PARTIAL)

            val constant = ConstantExpressionEvaluator.getConstant(this, c) ?: return null

            return constant.toConstantValue(getType(c) ?: return null)
        }
    }
}
