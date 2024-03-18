// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.java.JavaBundle
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.siblings
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor.*
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem.*
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.isConstant
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isNullableNothing

class KotlinConstantConditionsInspection : AbstractKotlinInspection() {
    private enum class ConstantValue {
        TRUE, FALSE, NULL, ZERO, UNKNOWN
    }

    var warnOnConstantRefs = true

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
            var newVal = when (val dfType = state.getDfType(value)) {
                DfTypes.TRUE -> ConstantValue.TRUE
                DfTypes.FALSE -> ConstantValue.FALSE
                DfTypes.NULL -> ConstantValue.NULL
                else -> {
                    val constVal: Number? = dfType.getConstantOfType(Number::class.java)
                    if (constVal != null && (constVal == 0 || constVal == 0L)) ConstantValue.ZERO
                    else ConstantValue.UNKNOWN
                }
            }
            if (oldVal != null && oldVal != newVal) {
                newVal = ConstantValue.UNKNOWN
            }
            constantConditions[anchor] = newVal
        }
    }

    override fun getOptionsPane(): OptPane {
        return pane(
            checkbox("warnOnConstantRefs", JavaBundle.message("inspection.data.flow.warn.when.reading.a.value.guaranteed.to.be.constant"))
        )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // Non-JVM is not supported now
        if (holder.file.module?.platform?.isJvm() != true) return PsiElementVisitor.EMPTY_VISITOR
        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                if (shouldAnalyzeProperty(property)) {
                    val initializer = property.delegateExpressionOrInitializer ?: return
                    analyze(initializer)
                }
            }

            override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                if (shouldAnalyzeProperty(accessor.property)) {
                    val bodyExpression = accessor.bodyExpression ?: accessor.bodyBlockExpression ?: return
                    analyze(bodyExpression)
                }
            }

            override fun visitParameter(parameter: KtParameter) {
                analyze(parameter.defaultValue ?: return)
            }

            private fun shouldAnalyzeProperty(property: KtProperty) =
                property.isTopLevel || property.parent is KtClassBody

            override fun visitClassInitializer(initializer: KtClassInitializer) {
                analyze(initializer.body ?: return)
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                val body = function.bodyExpression ?: function.bodyBlockExpression ?: return
                analyze(body)
            }

            private fun analyze(body: KtExpression) {
                val factory = DfaValueFactory(holder.project)
                processDataflowAnalysis(factory, body, holder, listOf(JvmDfaMemoryStateImpl(factory)))
            }
        }
    }

    private fun processDataflowAnalysis(
        factory: DfaValueFactory,
        body: KtExpression,
        holder: ProblemsHolder,
        states: Collection<DfaMemoryState>
    ) {
        val flow = DataFlowIRProvider.forElement(body, factory) ?: return
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
                                    if (shouldReportAsValue(cv, expr))
                                        "inspection.message.value.always.true"
                                    else if (logicalChain(expr))
                                        "inspection.message.condition.always.true.when.reached"
                                    else
                                        "inspection.message.condition.always.true"
                                ConstantValue.FALSE ->
                                    if (shouldReportAsValue(cv, expr))
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
                                if (shouldReportAsValue(cv, expr)) ProblemHighlightType.WEAK_WARNING
                                else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            if (warnOnConstantRefs || highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
                                holder.registerProblem(expr, KotlinBundle.message(key, expr.text), highlightType)
                            }
                        }
                    }
                    is KotlinWhenConditionAnchor -> {
                        val condition = anchor.condition
                        if (!shouldSuppressWhenCondition(cv, condition)) {
                            val message = KotlinBundle.message("inspection.message.when.condition.always.false")
                            if (cv == ConstantValue.FALSE) {
                                holder.registerProblem(condition, message)
                            } else if (cv == ConstantValue.TRUE) {
                                condition.siblings(forward = true, withSelf = false)
                                    .filterIsInstance<KtWhenCondition>()
                                    .filter { cond -> cond.text.isNotEmpty() }
                                    .forEach { cond -> holder.registerProblem(cond, message) }
                                val nextEntry = condition.parent as? KtWhenEntry ?: return@forEach
                                nextEntry.siblings(forward = true, withSelf = false)
                                    .filterIsInstance<KtWhenEntry>()
                                    .filterNot { entry -> entry.isElse }
                                    .flatMap { entry -> entry.conditions.asSequence() }
                                    .filter { cond -> cond.text.isNotEmpty() }
                                    .forEach { cond -> holder.registerProblem(cond, message) }
                            }
                        }
                    }
                    is KotlinForVisitedAnchor -> {
                        val loopRange = anchor.forExpression.loopRange!!
                        if (cv == ConstantValue.FALSE && !shouldSuppressForCondition(loopRange)) {
                            val message = KotlinBundle.message("inspection.message.for.never.visited")
                            holder.registerProblem(loopRange, message)
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

    private fun shouldSuppressForCondition(loopRange: KtExpression): Boolean {
        if (loopRange is KtBinaryExpression) {
            val left = loopRange.left
            val right = loopRange.right
            // Reported separately by EmptyRangeInspection
            return left != null && right != null && left.isConstant() && right.isConstant()
        }
        return false
    }

    private fun shouldReportAsValue(cv: ConstantValue, expr: KtExpression): Boolean {
        if (expr !is KtSimpleNameExpression && !(expr is KtQualifiedExpression && expr.selectorExpression is KtSimpleNameExpression)) {
            return false
        }
        var parent = expr.parent
        while (parent is KtParenthesizedExpression) parent = parent.parent
        if (parent is KtBinaryExpression && (cv == ConstantValue.TRUE || cv == ConstantValue.FALSE)) return false
        if (parent is KtContainerNode && parent.parent is KtIfExpression) return false
        return parent !is KtWhenConditionWithExpression
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

    private fun shouldSuppressWhenCondition(
        cv: ConstantValue,
        condition: KtWhenCondition
    ): Boolean {
        if (cv != ConstantValue.FALSE && cv != ConstantValue.TRUE) return true
        if (cv == ConstantValue.TRUE && isLastCondition(condition)) return true
        if (condition.textLength == 0) return true
        return isCompilationWarning(condition)
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

    companion object {
        private fun areEquivalent(e1: KtElement, e2: KtElement): Boolean {
            return PsiEquivalenceUtil.areEquivalent(e1, e2,
                                                    {ref1, ref2 -> ref1.element.text.equals(ref2.element.text)},
                                                    null, null, false)
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
                return areEquivalent(left, templateLeft) && areEquivalent(right.negate(false), templateRight)
            }
            if (!areEquivalent(right, templateRight)) return false
            if (templateLeft === expression) {
                return areEquivalent(left.negate(false), templateLeft)
            }
            if (templateLeft !is KtBinaryExpression || templateLeft.operationToken !== KtTokens.ANDAND) return false
            return isOppositeCondition(left, templateLeft, expression)
        }

        private fun hasOppositeCondition(whenExpression: KtWhenExpression, topCondition: KtExpression, expression: KtExpression): Boolean {
            for (entry in whenExpression.entries) {
                for (condition in entry.conditions) {
                    if (condition is KtWhenConditionWithExpression) {
                        val candidate = condition.expression
                        if (candidate === topCondition) return false
                        if (topCondition is KtBinaryExpression && isOppositeCondition(candidate, topCondition, expression)) return true
                        if (candidate != null && areEquivalent(expression.negate(false), candidate)) return true
                    }
                }
            }
            return false
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
            if (parent is KtWhenConditionWithExpression) {
                val whenExpression = (parent.parent as? KtWhenEntry)?.parent as? KtWhenExpression
                if (whenExpression != null && hasOppositeCondition(whenExpression, expression, expression)) {
                    return true
                }
            }
            return false
        }

        private fun isCompilationWarning(anchor: KtElement): Boolean
        {
            val context = anchor.analyze(BodyResolveMode.FULL)
            if (context.diagnostics.forElement(anchor).any
                { it.factory == Errors.CAST_NEVER_SUCCEEDS
                        || it.factory == Errors.SENSELESS_COMPARISON
                        || it.factory == Errors.SENSELESS_NULL_IN_WHEN
                        || it.factory == Errors.USELESS_IS_CHECK
                        || it.factory == Errors.DUPLICATE_LABEL_IN_WHEN }
            ) {
                return true
            }

            val rootElement = anchor.containingFile
            val suppressionCache = KotlinCacheService.getInstance(anchor.project).getSuppressionCache()
            return suppressionCache.isSuppressed(anchor, rootElement, "CAST_NEVER_SUCCEEDS", Severity.WARNING) ||
                    suppressionCache.isSuppressed(anchor, rootElement, "SENSELESS_COMPARISON", Severity.WARNING) ||
                    suppressionCache.isSuppressed(anchor, rootElement, "SENSELESS_NULL_IN_WHEN", Severity.WARNING) ||
                    suppressionCache.isSuppressed(anchor, rootElement, "USELESS_IS_CHECK", Severity.WARNING) ||
                    suppressionCache.isSuppressed(anchor, rootElement, "DUPLICATE_LABEL_IN_WHEN", Severity.WARNING)
        }

        private fun isCallToMethod(call: KtCallExpression, packageName: String, methodName: String): Boolean {
            val descriptor = call.resolveToCall()?.resultingDescriptor ?: return false
            if (descriptor.name.asString() != methodName) return false
            val packageFragment = descriptor.containingDeclaration as? PackageFragmentDescriptor ?: return false
            return packageFragment.fqName.asString() == packageName
        }

        // Do not report x.let { true } or x.let { false } as it's pretty evident
        private fun isLetConstant(expr: KtExpression): Boolean {
            val call = (expr as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression ?: return false
            if (!isCallToMethod(call, "kotlin", "let")) return false
            val lambda = call.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return false
            return lambda.bodyExpression?.statements?.singleOrNull() is KtConstantExpression
        }

        // Do not report on also, as it always returns the qualifier. If necessary, qualifier itself will be reported
        private fun isAlsoChain(expr: KtExpression): Boolean {
            val call = (expr as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression ?: return false
            return isCallToMethod(call, "kotlin", "also")
        }

        private fun isAssertion(parent: PsiElement?, value: Boolean): Boolean {
            return when (parent) {
                is KtBinaryExpression ->
                    (parent.operationToken == KtTokens.ANDAND || parent.operationToken == KtTokens.OROR) && isAssertion(parent.parent, value)
                is KtParenthesizedExpression ->
                    isAssertion(parent.parent, value)
                is KtPrefixExpression ->
                    parent.operationToken == KtTokens.EXCL && isAssertion(parent.parent, !value)
                is KtValueArgument -> {
                    if (!value) return false
                    val valueArgList = parent.parent as? KtValueArgumentList ?: return false
                    val call = valueArgList.parent as? KtCallExpression ?: return false
                    val descriptor = call.resolveToCall()?.resultingDescriptor ?: return false
                    val name = descriptor.name.asString()
                    if (name != "assert" && name != "require" && name != "check") return false
                    val pkg = descriptor.containingDeclaration as? PackageFragmentDescriptor ?: return false
                    return pkg.fqName.asString() == "kotlin"
                }
                else -> false
            }
        }

        private fun hasWritesTo(block: PsiElement?, variable: KtProperty): Boolean {
            return !PsiTreeUtil.processElements(block, KtSimpleNameExpression::class.java) { ref ->
                val write = ref.mainReference.isReferenceTo(variable) && ref.readWriteAccess(false).isWrite
                !write
            }
        }

        private fun isUpdateChain(expression: KtExpression): Boolean {
            // x = x or ..., etc.
            if (expression !is KtSimpleNameExpression) return false
            val binOp = expression.parent as? KtBinaryExpression ?: return false
            val op = binOp.operationReference.text
            if (op != "or" && op != "and" && op != "xor" && op != "||" && op != "&&") return false
            val assignment = binOp.parent as? KtBinaryExpression ?: return false
            if (assignment.operationToken != KtTokens.EQ) return false
            val left = assignment.left
            if (left !is KtSimpleNameExpression || !left.textMatches(expression.text)) return false
            val variable = expression.mainReference.resolve() as? KtProperty ?: return false
            val varParent = variable.parent as? KtBlockExpression ?: return false
            var context: PsiElement = assignment
            var block = context.parent
            while (block is KtContainerNode ||
                    block is KtBlockExpression && block.statements.first() == context ||
                    block is KtIfExpression && block.then?.parent == context && block.`else` == null && !hasWritesTo(block.condition, variable)
            ) {
                context = block
                block = context.parent
            }
            if (block !== varParent) return false
            var curExpression = variable.nextSibling
            while (curExpression != context) {
                if (hasWritesTo(curExpression, variable)) return false
                curExpression = curExpression.nextSibling
            }
            return true
        }

        fun shouldSuppress(value: DfType, expression: KtExpression): Boolean {
            val constant = when(value) {
                DfTypes.NULL -> ConstantValue.NULL
                DfTypes.TRUE -> ConstantValue.TRUE
                DfTypes.FALSE -> ConstantValue.FALSE
                DfTypes.intValue(0), DfTypes.longValue(0) -> ConstantValue.ZERO
                else -> ConstantValue.UNKNOWN
            }
            return shouldSuppress(constant, expression)
        }

        private fun shouldSuppress(value: ConstantValue, expression: KtExpression): Boolean {
            // TODO: return x && y.let {return...}
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
            if (expression is KtBinaryExpression && expression.operationToken == KtTokens.ELVIS) {
                // Left part of Elvis is Nothing?, so the right part is always executed
                // Could be caused by code like return x?.let { return ... } ?: true
                // While inner "return" is redundant, the "always true" warning is confusing
                // probably separate inspection could report extra "return"
                if (expression.left?.getKotlinType()?.isNullableNothing() == true) {
                    return true
                }
            }
            if (isAlsoChain(expression) || isLetConstant(expression) || isUpdateChain(expression)) return true
            val kotlinType = expression.getKotlinType() ?: return false
            when (value) {
                ConstantValue.TRUE -> {
                    if (isAndOrConditionWithNothingOperand(expression, KtTokens.OROR)) return true
                    if (isSmartCastNecessary(expression, true)) return true
                    if (isPairingConditionInWhen(expression)) return true
                    if (isAssertion(parent, true)) return true
                }
                ConstantValue.FALSE -> {
                    if (isAndOrConditionWithNothingOperand(expression, KtTokens.ANDAND)) return true
                    if (isSmartCastNecessary(expression, false)) return true
                    if (isAssertion(parent, false)) return true
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
                        if (receiver is KtSimpleNameExpression &&
                            receiver.resolveMainReferenceToDescriptors()
                                .any { desc -> desc is ClassDescriptor && desc.kind == ClassKind.ENUM_ENTRY }
                        ) {
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
                    if (parent is KtBinaryExpression) {
                        val token = parent.operationToken
                        if (token === KtTokens.EQEQ || token === KtTokens.EXCLEQ || token === KtTokens.EQEQEQ || token === KtTokens.EXCLEQEQEQ) {
                            // like if (x == 0) when 'x' is known to be 0: report 'always true' instead
                            val left = parent.left
                            if (left != null && ConstantExpressionEvaluator.getConstant(left, bindingContext)?.getValue(kotlinType) == 0) return true
                            val right = parent.right
                            if (right != null && ConstantExpressionEvaluator.getConstant(right, bindingContext)?.getValue(kotlinType) == 0) return true
                        }
                    }
                }
                ConstantValue.NULL -> {
                    if (parent is KtProperty && parent.typeReference == null && expression is KtSimpleNameExpression) {
                        // initialize other variable with null to copy type, like
                        // var x1 : X = null
                        // var x2 = x1 -- let's suppress this
                        return true
                    }
                    if (kotlinType.constructor.declarationDescriptor is TypeParameterDescriptor) {
                        // Do not report always-null when expected expression type is the same type parameter
                        // as it's not possible to replace it with a null literal without an unchecked cast 
                        val expectedType = expression.analyze(BodyResolveMode.FULL).get(BindingContext.EXPECTED_EXPRESSION_TYPE, expression)
                        if (expectedType == kotlinType) return true
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
                    if (kotlinType.toDfType() == DfTypes.NULL) {
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

        // x || return y
        private fun isAndOrConditionWithNothingOperand(expression: KtExpression, token: KtSingleValueToken): Boolean {
            if (expression !is KtBinaryExpression || expression.operationToken != token) return false
            val type = expression.right?.getKotlinType()
            return type != null && type.isNothing()
        }
    }
}