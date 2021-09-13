// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInsight.PsiEquivalenceUtil
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.*
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.*
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
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
        // TODO: suppress when condition is required for a smart cast
        var parent = expression.parent
        if (parent is KtDotQualifiedExpression && parent.selectorExpression == expression) {
            // Will be reported for parent qualified expression
            return true
        }
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
        when (value) {
            ConstantValue.TRUE -> {
                if (isSmartCastNecessary(expression, true)) return true
                if (isPairingConditionInWhen(expression)) return true
                if (isAssertion(parent)) return true
            }
            ConstantValue.FALSE -> {
                if (isSmartCastNecessary(expression, false)) return true
            }
            ConstantValue.ZERO -> {
                if (expression.readWriteAccess(false).isWrite) {
                    // like if (x == 0) x++, warning would be somewhat annoying
                    return true
                }
                if (expression is KtDotQualifiedExpression && expression.selectorExpression?.textMatches("ordinal") == true) {
                    var receiver: KtExpression? = expression.receiverExpression
                    if (receiver is KtQualifiedExpression) {
                        receiver = receiver.selectorExpression
                    }
                    if (receiver is KtSimpleNameExpression && receiver.mainReference.resolve() is KtEnumEntry) {
                        // ordinal() call on explicit enum constant
                        return true
                    }
                }
                val bindingContext = expression.analyze()
                if (ConstantExpressionEvaluator.getConstant(expression, bindingContext) != null) return true
                if (expression is KtSimpleNameExpression &&
                    (parent is KtValueArgument || parent is KtContainerNode && parent.parent is KtArrayAccessExpression)
                ) {
                    // zero value is passed as argument to another method or used for array access. Often, such a warning is annoying
                    return true
                }
            }
            ConstantValue.NULL -> {
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
                val kotlinType = expression.getKotlinType()
                if (kotlinType.toDfType(expression) == DfTypes.NULL) {
                    // According to type system, nothing but null could be stored in such an expression (likely "Void?" type)
                    return true
                }
            }
            else -> {}
        }
        if (expression is KtSimpleNameExpression) {
            val target = expression.mainReference.resolve()
            if (target is KtProperty && !target.isVar && target.initializer is KtConstantExpression) {
                // suppress warnings uses of boolean constant like 'val b = true'
                return true
            }
        }
        if (isCompilationWarning(expression)) {
            return true
        }
        return expression.isUsedAsStatement(expression.analyze(BodyResolveMode.FULL))
    }

    private fun isAssertion(parent: PsiElement?): Boolean {
        val valueArg = parent as? KtValueArgument ?: return false
        val valueArgList = valueArg.parent as? KtValueArgumentList ?: return false
        val call = valueArgList.parent as? KtCallExpression ?: return false
        val descriptor = call.resolveToCall()?.resultingDescriptor ?: return false
        val name = descriptor.name.asString()
        if (name != "assert" && name != "require") return false
        val pkg = descriptor.containingDeclaration as? PackageFragmentDescriptor ?: return false
        return pkg.fqName.asString() == "kotlin"
    }

    /**
     * Returns true if expression is part of when condition expression that looks like
     * ```
     * when {
     * a && b -> ...
     * a && !b -> ...
     * }
     * ```
     * In this case, !b could be reported as 'always true' but such warnings are annoying
     */
    private fun isPairingConditionInWhen(expression: KtExpression): Boolean {
        val parent = expression.parent
        if (parent is KtBinaryExpression && parent.operationToken == KtTokens.ANDAND) {
            var topAnd: KtBinaryExpression = parent
            while (true) {
                val nextParent = topAnd.parent
                if (nextParent is KtBinaryExpression && nextParent.operationToken == KtTokens.ANDAND) {
                    topAnd = nextParent
                } else break
            }
            val topAndParent = topAnd.parent
            if (topAndParent is KtWhenConditionWithExpression) {
                val whenExpression = (topAndParent.parent as? KtWhenEntry)?.parent as? KtWhenExpression
                if (whenExpression != null && hasOppositeCondition(whenExpression, topAnd, expression)) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasOppositeCondition(whenExpression: KtWhenExpression, topAnd: KtBinaryExpression, expression: KtExpression): Boolean {
        for (entry in whenExpression.entries) {
            for (condition in entry.conditions) {
                if (condition is KtWhenConditionWithExpression) {
                    val candidate = condition.expression
                    if (candidate === topAnd) return false
                    if (isOppositeCondition(candidate, topAnd, expression)) return true
                }
            }
        }
        return false
    }

    private tailrec fun isOppositeCondition(candidate: KtExpression?, template: KtBinaryExpression, expression: KtExpression): Boolean {
        if (candidate !is KtBinaryExpression || candidate.operationToken !== KtTokens.ANDAND) return false
        val left = candidate.left
        val right = candidate.right
        if (left == null || right == null) return false
        val templateLeft = template.left
        val templateRight = template.right
        if (templateLeft == null || templateRight == null) return false
        if (templateRight === expression) {
            return areEquivalent(left, templateLeft) && areEquivalent(right.negate(), templateRight)
        }
        if (!areEquivalent(right, templateRight)) return false
        if (templateLeft === expression) {
            return areEquivalent(left.negate(), templateLeft)
        }
        if (templateLeft !is KtBinaryExpression || templateLeft.operationToken !== KtTokens.ANDAND) return false
        return isOppositeCondition(left, templateLeft, expression)
    }

    private fun areEquivalent(e1: KtElement, e2: KtElement): Boolean {
        return PsiEquivalenceUtil.areElementsEquivalent(e1, e2,
                                                        {ref1, ref2 -> ref1.element.text.compareTo(ref2.element.text)},
                                                        null, null, false)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // Non-JVM is not supported now
        if (holder.file.module?.platform?.isJvm() != true) return PsiElementVisitor.EMPTY_VISITOR
        return namedFunctionVisitor(fun(function) {
            val body = function.bodyExpression ?: function.bodyBlockExpression ?: return
            val factory = DfaValueFactory(holder.project)
            processDataflowAnalysis(factory, body, holder, listOf(JvmDfaMemoryStateImpl(factory)))
        })
    }

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
                                ConstantValue.TRUE ->
                                    if (expr is KtSimpleNameExpression || expr is KtQualifiedExpression)
                                        "inspection.message.value.always.true"
                                    else if (logicalChain(expr))
                                        "inspection.message.condition.always.true.when.reached" 
                                    else 
                                        "inspection.message.condition.always.true"
                                ConstantValue.FALSE ->
                                    if (expr is KtSimpleNameExpression || expr is KtQualifiedExpression)
                                        "inspection.message.value.always.false"
                                    else if (logicalChain(expr))
                                        "inspection.message.condition.always.false.when.reached"
                                    else
                                        "inspection.message.condition.always.false"
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
                    is KotlinForVisitedAnchor -> {
                        if (cv == ConstantValue.FALSE) {
                            val message = KotlinBundle.message("inspection.message.for.never.visited")
                            holder.registerProblem(anchor.forExpression.loopRange!!, message)
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
                    is KotlinNullCheckProblem -> {
                        val expr = problem.expr
                        if (expr.baseExpression?.isNull() != true) {
                            holder.registerProblem(expr.operationReference, KotlinBundle.message("inspection.message.nonnull.cast.will.always.fail"))
                        }
                    }
                    is KotlinCastProblem -> {
                        val anchor = (problem.cast as? KtBinaryExpressionWithTypeRHS)?.operationReference ?: problem.cast
                        if (!isCompilationWarning(anchor)) {
                            holder.registerProblem(anchor, KotlinBundle.message("inspection.message.cast.will.always.fail"))
                        }
                    }
                }
            }
        }
    }

    private fun logicalChain(expr: KtExpression): Boolean {
        var context = expr
        var parent = context.parent
        while (parent is KtParenthesizedExpression) {
            context = parent
            parent = context.parent
        }
        if (parent is KtBinaryExpression && parent.right == context) {
            val token = parent.operationToken
            return token == KtTokens.ANDAND || token == KtTokens.OROR
        }
        return false
    }

    private fun isCompilationWarning(anchor: KtExpression): Boolean
    {
        val context = anchor.analyze(BodyResolveMode.FULL)
        if (context.diagnostics.forElement(anchor).any
            { it.factory == Errors.CAST_NEVER_SUCCEEDS || it.factory == Errors.SENSELESS_COMPARISON || it.factory == Errors.USELESS_IS_CHECK }
        ) {
            return true
        }
        val suppressionCache = KotlinCacheService.getInstance(anchor.project).getSuppressionCache()
        return suppressionCache.isSuppressed(anchor, "CAST_NEVER_SUCCEEDS", Severity.WARNING) ||
                suppressionCache.isSuppressed(anchor, "SENSELESS_COMPARISON", Severity.WARNING) ||
                suppressionCache.isSuppressed(anchor, "USELESS_IS_CHECK", Severity.WARNING)
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
        if (entry.conditions.last() == condition) {
            val entries = whenExpr.entries
            val lastEntry = entries.last()
            if (lastEntry == entry) return true
            val size = entries.size
            // Also, do not report the always reachable entry right before 'else',
            // usually it's necessary for the smart-cast, or for definite assignment, and the report is just noise
            if (lastEntry.isElse && size > 1 && entries[size - 2] == entry) return true
        }
        return false
    }
}