// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.lang.DfaListener
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinExpressionAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.KotlinWhenConditionAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.KotlinArrayIndexProblem
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.KotlinCastProblem
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinConstantConditionsInspection : AbstractKotlinInspection() {
    private enum class ConstantValue {
        TRUE, FALSE, NULL, ZERO, UNKNOWN
    }

    private class KotlinDfaListener : DfaListener {
        val constantConditions = hashMapOf<KotlinAnchor, ConstantValue>()
        val problems = hashMapOf<KotlinProblem, ThreeState>()

        override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
            if (anchor is KotlinAnchor) {
                recordExpressionValue(anchor, state, value)
            }
        }

        override fun onCondition(problem: UnsatisfiedConditionProblem, value: DfaValue, failed: ThreeState, state: DfaMemoryState) {
            if (problem is KotlinProblem) {
                problems.merge(problem, failed, ThreeState::merge)
            }
        }

        private fun recordExpressionValue(anchor: KotlinAnchor, state: DfaMemoryState, value: DfaValue) {
            val oldVal = constantConditions[anchor]
            if (oldVal == ConstantValue.UNKNOWN) return
            var newVal = when (state.getDfType(value)) {
                DfTypes.TRUE -> ConstantValue.TRUE
                DfTypes.FALSE -> ConstantValue.FALSE
                DfTypes.NULL -> ConstantValue.NULL
                DfTypes.intValue(0), DfTypes.longValue(0) -> ConstantValue.ZERO
                else -> ConstantValue.UNKNOWN
            }
            if (oldVal != null && oldVal != newVal) {
                newVal = ConstantValue.UNKNOWN
            }
            constantConditions[anchor] = newVal
        }
    }

    private fun shouldSuppress(value: ConstantValue, expression: KtExpression): Boolean {
        var parent = expression.parent
        while (parent is KtParenthesizedExpression) {
            parent = parent.parent
        }
        if (expression is KtConstantExpression ||
            // If result of initialization is constant, then the initializer will be reported
            expression is KtProperty ||
            // If result of assignment is constant, then the right-hand part will be reported
            expression is KtBinaryExpression && expression.operationToken == KtTokens.EQ ||
            // Negation operand: negation itself will be reported
            (parent as? KtPrefixExpression)?.operationToken == KtTokens.EXCL
        ) {
            return true
        }
        if (value == ConstantValue.ZERO) {
            if (expression.readWriteAccess(false).isWrite) {
                // like if (x == 0) x++, warning would be somewhat annoying
                return true
            }
            if (expression is KtSimpleNameExpression &&
                (parent is KtValueArgument || parent is KtContainerNode && parent.parent is KtArrayAccessExpression)) {
                // zero value is passed as argument to another method or used for array access. Often, such a warning is annoying
                return true
            }
        }
        if (value == ConstantValue.NULL) {
            if (parent is KtProperty && parent.typeReference == null && expression is KtSimpleNameExpression) {
                // initialize other variable with null to copy type, like
                // var x1 : X = null
                // var x2 = x1 -- let's suppress this
                return true
            }
            if (expression is KtBinaryExpressionWithTypeRHS && expression.left.isNull()) {
                // like (null as? X)
                return true
            }
            if (parent is KtBinaryExpression) {
                val token = parent.operationToken
                if ((token === KtTokens.EQEQ || token === KtTokens.EXCLEQ || token === KtTokens.EQEQEQ || token === KtTokens.EXCLEQEQEQ) &&
                    (parent.left?.isNull() == true || parent.right?.isNull() == true)
                ) {
                    // like if (x == null) when 'x' is known to be null: report 'always true' instead
                    return true
                }
            }
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
                .any { it.factory == Errors.SENSELESS_COMPARISON || it.factory == Errors.USELESS_IS_CHECK }
        ) {
            return true
        }
        return expression.isUsedAsStatement(context)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = namedFunctionVisitor(fun(function) {
        val body = function.bodyExpression ?: function.bodyBlockExpression ?: return
        val factory = DfaValueFactory(holder.project)
        processDataflowAnalysis(factory, body, holder, listOf(JvmDfaMemoryStateImpl(factory)))
    })

    private fun processDataflowAnalysis(
        factory: DfaValueFactory,
        body: KtExpression,
        holder: ProblemsHolder,
        states: Collection<DfaMemoryState>
    ) {
        val flow = KtControlFlowBuilder(factory, body).buildFlow() ?: return
        val listener = KotlinDfaListener()
        val interpreter = StandardDataFlowInterpreter(flow, listener)
        if (interpreter.interpret(states.map { s -> DfaInstructionState(flow.getInstruction(0), s) }) != RunnerResult.OK) return
        reportProblems(listener, holder)
        for ((closure, closureStates) in interpreter.closures.entrySet()) {
            if (closure is KtExpression) {
                processDataflowAnalysis(factory, closure, holder, closureStates)
            }
        }
    }

    private fun reportProblems(
        listener: KotlinDfaListener,
        holder: ProblemsHolder
    ) {
        listener.constantConditions.forEach { (anchor, cv) ->
            if (cv != ConstantValue.UNKNOWN) {
                when (anchor) {
                    is KotlinExpressionAnchor -> {
                        val expr = anchor.expression
                        if (!shouldSuppress(cv, expr)) {
                            val key = when (cv) {
                                ConstantValue.TRUE -> "inspection.message.condition.always.true"
                                ConstantValue.FALSE -> "inspection.message.condition.always.false"
                                ConstantValue.NULL -> "inspection.message.value.always.null"
                                ConstantValue.ZERO -> "inspection.message.value.always.zero"
                                else -> throw IllegalStateException("Unexpected constant: $cv")
                            }
                            val highlightType =
                                if (expr is KtSimpleNameExpression || expr is KtQualifiedExpression) ProblemHighlightType.WEAK_WARNING
                                else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            holder.registerProblem(expr, KotlinBundle.message(key), highlightType)
                        }
                    }
                    is KotlinWhenConditionAnchor -> {
                        val condition = anchor.condition
                        if (!shouldSuppressWhenCondition(cv, condition)) {
                            val key = if (cv == ConstantValue.TRUE) "inspection.message.when.condition.always.true"
                            else "inspection.message.when.condition.always.false"
                            holder.registerProblem(condition, KotlinBundle.message(key))
                        }
                    }
                }
            }
        }
        listener.problems.forEach { (problem, state) ->
            if (state == ThreeState.YES) {
                when (problem) {
                    is KotlinArrayIndexProblem ->
                        holder.registerProblem(problem.index, KotlinBundle.message("inspection.message.index.out.of.bounds"))
                    is KotlinCastProblem ->
                        holder.registerProblem(
                            (problem.cast as? KtBinaryExpressionWithTypeRHS)?.operationReference ?: problem.cast,
                            KotlinBundle.message("inspection.message.cast.will.always.fail")
                        )
                }
            }
        }
    }

    private fun shouldSuppressWhenCondition(
        cv: ConstantValue,
        condition: KtWhenCondition
    ): Boolean {
        if (cv != ConstantValue.FALSE && cv != ConstantValue.TRUE) return true
        if (cv == ConstantValue.TRUE && isLastCondition(condition)) return true
        val context = condition.analyze(BodyResolveMode.FULL)
        if (context.diagnostics.forElement(condition).any { it.factory == Errors.USELESS_IS_CHECK }) return true
        return false
    }

    private fun isLastCondition(condition: KtWhenCondition): Boolean {
        val entry = condition.parent as? KtWhenEntry ?: return false
        val whenExpr = entry.parent as? KtWhenExpression ?: return false
        return entry.conditions.last() == condition && whenExpr.entries.last() == entry
    }
}