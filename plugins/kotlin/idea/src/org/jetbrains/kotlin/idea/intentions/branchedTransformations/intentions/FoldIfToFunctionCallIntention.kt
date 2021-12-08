// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.idea.util.reformatted
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class FoldIfToFunctionCallIntention : SelfTargetingRangeIntention<KtIfExpression>(
    KtIfExpression::class.java,
    KotlinBundle.lazyMessage("lift.function.call.out.of.if"),
) {
    override fun applicabilityRange(element: KtIfExpression): TextRange? =
        if (canFoldToFunctionCall(element)) element.ifKeyword.textRange else null

    override fun applyTo(element: KtIfExpression, editor: Editor?) {
        foldToFunctionCall(element)
    }

    companion object {
        fun branches(expression: KtExpression): List<KtExpression>? {
            val branches = when (expression) {
                is KtIfExpression -> expression.branches
                is KtWhenExpression -> expression.entries.map { it.expression }
                else -> emptyList()
            }
            val branchesSize = branches.size
            if (branchesSize < 2) return null
            return branches.filterNotNull().takeIf { it.size == branchesSize }
        }

        private fun canFoldToFunctionCall(element: KtExpression): Boolean {
            val branches = branches(element) ?: return false

            val callExpressions = branches.mapNotNull { it.callExpression() }
            if (branches.size != callExpressions.size) return false

            if (differentArgumentIndex(callExpressions) == null) return false

            val headCall = callExpressions.first()
            val tailCalls = callExpressions.drop(1)
            val context = headCall.analyze(BodyResolveMode.PARTIAL)
            val (headFunctionFqName, headFunctionParameters) = headCall.fqNameAndParameters(context) ?: return false
            return tailCalls.all { call ->
                val (fqName, parameters) = call.fqNameAndParameters(context) ?: return@all false
                fqName == headFunctionFqName && parameters.zip(headFunctionParameters).all { it.first == it.second }
            }
        }

        private fun foldToFunctionCall(element: KtExpression) {
            val branches = branches(element) ?: return

            val callExpressions = branches.mapNotNull { it.callExpression() }
            val headCall = callExpressions.first()
            val argumentIndex = differentArgumentIndex(callExpressions) ?: return
            val hasNamedArgument = callExpressions.any { call -> call.valueArguments.any { it.getArgumentName() != null } }

            val copiedIf = element.copy() as KtIfExpression
            copiedIf.branches.forEach { branch ->
                val call = branch.callExpression() ?: return
                val argument = call.valueArguments[argumentIndex].getArgumentExpression() ?: return
                call.getQualifiedExpressionForSelectorOrThis().replace(argument)
            }
            headCall.valueArguments[argumentIndex].getArgumentExpression()?.replace(copiedIf)
            if (hasNamedArgument) {
                headCall.valueArguments.forEach {
                    if (it.getArgumentName() == null) AddNameToArgumentIntention.apply(it)
                }
            }
            element.replace(headCall.getQualifiedExpressionForSelectorOrThis()).reformatted()
        }

        private fun differentArgumentIndex(callExpressions: List<KtCallExpression>): Int? {
            val headCall = callExpressions.first()
            val headCalleeText = headCall.calleeText()
            val tailCalls = callExpressions.drop(1)

            if (headCall.valueArguments.any { it is KtLambdaArgument }) return null
            val headArguments = headCall.valueArguments.mapNotNull { it.getArgumentExpression()?.text }
            val headArgumentsSize = headArguments.size
            if (headArgumentsSize != headCall.valueArguments.size) return null
            val differentArgumentIndexes = tailCalls.mapNotNull { call ->
                if (call.calleeText() != headCalleeText) return@mapNotNull null
                val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression()?.text }
                if (arguments.size != headArgumentsSize) return@mapNotNull null
                val differentArgumentIndexes = arguments.zip(headArguments).mapIndexedNotNull { index, (arg, headArg) ->
                    if (arg != headArg) index else null
                }
                differentArgumentIndexes.singleOrNull()
            }
            if (differentArgumentIndexes.size != tailCalls.size || differentArgumentIndexes.distinct().size != 1) return null

            return differentArgumentIndexes.first()
        }

        private fun KtExpression?.callExpression(): KtCallExpression? {
            return when (val expression = if (this is KtBlockExpression) statements.singleOrNull() else this) {
                is KtCallExpression -> expression
                is KtQualifiedExpression -> expression.callExpression
                else -> null
            }?.takeIf { it.calleeExpression != null }
        }

        private fun KtCallExpression.calleeText(): String {
            val parent = this.parent
            val (receiver, op) = if (parent is KtQualifiedExpression) {
                parent.receiverExpression.text to parent.operationSign.value
            } else {
                "" to ""
            }
            return "$receiver$op${calleeExpression?.text.orEmpty()}"
        }

        private fun KtCallExpression.fqNameAndParameters(context: BindingContext): Pair<FqName, List<ValueParameterDescriptor>>? {
            val resolvedCall = getResolvedCall(context) ?: return null
            val fqName = resolvedCall.resultingDescriptor.fqNameOrNull() ?: return null
            val parameters = valueArguments.mapNotNull { (resolvedCall.getArgumentMapping(it) as? ArgumentMatch)?.valueParameter }
            return fqName to parameters
        }
    }
}
