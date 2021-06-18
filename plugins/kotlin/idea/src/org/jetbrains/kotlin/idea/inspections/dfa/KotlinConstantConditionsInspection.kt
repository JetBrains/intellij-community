// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.lang.DfaListener
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinExpressionAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinWhenConditionAnchor
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinConstantConditionsInspection : AbstractKotlinInspection() {
    private enum class ConstantValue {
        TRUE, FALSE, UNKNOWN
    }

    private class KotlinDfaListener : DfaListener {
        val constantConditions = hashMapOf<KotlinAnchor, ConstantValue>()

        override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
            if (anchor is KotlinAnchor) {
                recordExpressionValue(anchor, state, value)
            }
        }

        private fun recordExpressionValue(anchor: KotlinAnchor, state: DfaMemoryState, value: DfaValue) {
            val oldVal = constantConditions[anchor]
            if (oldVal == ConstantValue.UNKNOWN) return
            var newVal = when (state.getDfType(value)) {
                DfTypes.TRUE -> ConstantValue.TRUE
                DfTypes.FALSE -> ConstantValue.FALSE
                else -> ConstantValue.UNKNOWN
            }
            if (oldVal != null && oldVal != newVal) {
                newVal = ConstantValue.UNKNOWN
            }
            constantConditions[anchor] = newVal
        }
    }

    private fun shouldSuppress(expression: KtExpression): Boolean {
        if (expression is KtConstantExpression ||
            // If result of initialization is constant, then the initializer will be reported
            expression is KtProperty ||
            // If result of assignment is constant, then the right-hand part will be reported
            expression is KtBinaryExpression && expression.operationToken == KtTokens.EQ ||
            // Negation operand: negation itself will be reported
            (expression.parent as? KtPrefixExpression)?.operationToken == KtTokens.EXCL
        ) {
            return true
        }
        if (expression is KtSimpleNameExpression) {
            val target = expression.mainReference.resolve()
            if (target is KtProperty && !target.isVar && target.initializer is KtConstantExpression) {
                // suppress warnings uses of boolean constant like 'val b = true'
                return true
            }
        }
        val context = expression.analyze(BodyResolveMode.FULL)
        if (context.diagnostics.forElement(expression)
            .any { it.factory == Errors.SENSELESS_COMPARISON || it.factory == Errors.USELESS_IS_CHECK }) {
            return true
        }
        return expression.isUsedAsStatement(context)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = namedFunctionVisitor(fun(function) {
        val body = function.bodyExpression ?: function.bodyBlockExpression ?: return
        val factory = DfaValueFactory(holder.project)
        val flow = KtControlFlowBuilder(factory, body).buildFlow() ?: return
        val state = JvmDfaMemoryStateImpl(factory)
        val listener = KotlinDfaListener()
        val interpreter = StandardDataFlowInterpreter(flow, listener)
        if (interpreter.interpret(state) != RunnerResult.OK) return
        listener.constantConditions.forEach { (anchor, cv) ->
            if (cv != ConstantValue.UNKNOWN) {
                when (anchor) {
                    is KotlinExpressionAnchor -> {
                        val expr = anchor.expression
                        if (!shouldSuppress(expr)) {
                            val key = if (cv == ConstantValue.TRUE) "inspection.message.condition.always.true"
                            else "inspection.message.condition.always.false"
                            val highlightType =
                                if (expr is KtSimpleNameExpression || expr is KtQualifiedExpression) ProblemHighlightType.WEAK_WARNING
                                else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            holder.registerProblem(expr, KotlinBundle.message(key), highlightType)
                        }
                    }
                    is KotlinWhenConditionAnchor -> {
                        val condition = anchor.condition
                        if (cv != ConstantValue.TRUE || !isLastCondition(condition)) {
                            val key = if (cv == ConstantValue.TRUE) "inspection.message.when.condition.always.true"
                            else "inspection.message.when.condition.always.false"
                            holder.registerProblem(condition, KotlinBundle.message(key))
                        }
                    }
                }
            }
        }
    })

    private fun isLastCondition(condition: KtWhenCondition): Boolean {
        val entry = condition.parent as? KtWhenEntry ?: return false
        val whenExpr = entry.parent as? KtWhenExpression ?: return false
        return entry.conditions.last() == condition && whenExpr.entries.last() == entry
    }
}