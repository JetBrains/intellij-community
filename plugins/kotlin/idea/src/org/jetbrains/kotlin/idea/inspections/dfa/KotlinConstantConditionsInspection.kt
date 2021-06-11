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
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.move.moveMethod.type
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

class KotlinConstantConditionsInspection : AbstractKotlinInspection() {
    private enum class ConstantValue {
        TRUE, FALSE, UNKNOWN
    }

    private class KotlinDfaListener : DfaListener {
        val constantConditions = hashMapOf<KtExpression, ConstantValue>()

        override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
            if (anchor is KotlinExpressionAnchor) {
                recordExpressionValue(anchor.expression, state, value)
            }
        }

        private fun recordExpressionValue(expression: KtExpression, state: DfaMemoryState, value: DfaValue) {
            val oldVal = constantConditions[expression]
            if (oldVal == ConstantValue.UNKNOWN) return
            var newVal = when (state.getDfType(value)) {
                DfTypes.TRUE -> ConstantValue.TRUE
                DfTypes.FALSE -> ConstantValue.FALSE
                else -> ConstantValue.UNKNOWN
            }
            if (oldVal != null && oldVal != newVal) {
                newVal = ConstantValue.UNKNOWN
            }
            constantConditions[expression] = newVal
        }
    }

    private fun shouldSuppress(expression: KtExpression): Boolean {
        if (expression is KtConstantExpression || expression is KtProperty ||
            expression is KtBinaryExpression && expression.operationToken == KtTokens.EQ
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
        return resultIsUnused(expression)
    }

    private fun resultIsUnused(expression: KtExpression): Boolean {
        return when (val parent = expression.parent) {
            is KtBlockExpression -> parent.lastBlockStatementOrThis() != expression || resultIsUnused(parent)
            is KtLoopExpression -> parent.body == expression
            is KtIfExpression -> resultIsUnused(parent)
            is KtWhenEntry -> {
                val whenExpression = parent.parent as? KtWhenExpression
                return whenExpression != null && resultIsUnused(whenExpression)
            }
            is KtFunction -> {
                val type = parent.type()
                return type != null && KotlinBuiltIns.isUnit(type)
            }
            else -> false
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = namedFunctionVisitor(fun(function) {
        val body = function.bodyExpression ?: function.bodyBlockExpression ?: return
        val factory = DfaValueFactory(holder.project)
        val flow = KtControlFlowBuilder(factory, body).buildFlow() ?: return
        val state = JvmDfaMemoryStateImpl(factory)
        val listener = KotlinDfaListener()
        val interpreter = StandardDataFlowInterpreter(flow, listener)
        if (interpreter.interpret(state) != RunnerResult.OK) return
        listener.constantConditions.forEach { (expr, cv) ->
            if (cv != ConstantValue.UNKNOWN && !shouldSuppress(expr)) {
                val key = if (cv == ConstantValue.TRUE) "inspection.message.condition.always.true"
                else "inspection.message.condition.always.false"
                val highlightType =
                    if (expr is KtSimpleNameExpression || expr is KtQualifiedExpression) ProblemHighlightType.WEAK_WARNING
                    else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                holder.registerProblem(expr, KotlinBundle.message(key), highlightType)
            }
        }
    })
}