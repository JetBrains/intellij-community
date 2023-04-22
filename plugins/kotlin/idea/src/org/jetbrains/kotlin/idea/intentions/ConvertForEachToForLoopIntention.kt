// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.getFactoryForImplicitReceiverWithSubtypeOf
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ConvertForEachToForLoopIntention : SelfTargetingOffsetIndependentIntention<KtSimpleNameExpression>(
    KtSimpleNameExpression::class.java, KotlinBundle.lazyMessage("replace.with.a.for.loop")
) {
    companion object {
        private const val FOR_EACH_NAME = "forEach"
        private val FOR_EACH_FQ_NAMES: Set<String> by lazy {
            sequenceOf("collections", "sequences", "text", "ranges").map { "kotlin.$it.$FOR_EACH_NAME" }.toSet()
        }

        private const val FOR_EACH_INDEXED_NAME = "forEachIndexed"
        private val FOR_EACH_INDEXED_FQ_NAMES: Set<String> by lazy {
            sequenceOf("collections", "sequences", "text", "ranges").map { "kotlin.$it.$FOR_EACH_INDEXED_NAME" }.toSet()
        }
    }

    override fun isApplicableTo(element: KtSimpleNameExpression): Boolean {
        val referencedName = element.getReferencedName()
        val isForEach = referencedName == FOR_EACH_NAME
        val isForEachIndexed = referencedName == FOR_EACH_INDEXED_NAME
        if (!isForEach && !isForEachIndexed) return false

        val data = extractData(element) ?: return false
        val valueParameterSize = data.functionLiteral.valueParameters.size
        if (isForEach && valueParameterSize > 1 || isForEachIndexed && valueParameterSize != 2) return false
        if (data.functionLiteral.bodyExpression == null) return false

        return true
    }

    override fun applyTo(element: KtSimpleNameExpression, editor: Editor?) {
        val (expressionToReplace, receiver, functionLiteral, context) = extractData(element)!!

        val commentSaver = CommentSaver(expressionToReplace)

        val isForEachIndexed = element.getReferencedName() == FOR_EACH_INDEXED_NAME
        val loop = generateLoop(functionLiteral, receiver, isForEachIndexed, context)
        val result = expressionToReplace.replace(loop)
        val forExpression = result.safeAs<KtForExpression>() ?: result.collectDescendantsOfType<KtForExpression>().first()
        forExpression.loopParameter?.also { editor?.caretModel?.moveToOffset(it.startOffset) }

        commentSaver.restore(result)
    }

    private data class Data(
        val expressionToReplace: KtExpression,
        val receiver: KtExpression,
        val functionLiteral: KtLambdaExpression,
        val context: BindingContext
    )

    private fun extractData(nameExpr: KtSimpleNameExpression): Data? {
        val expression = when (val parent = nameExpr.parent) {
            is KtCallExpression -> parent.getQualifiedExpressionForSelectorOrThis()
            is KtBinaryExpression -> parent
            else -> null
        } ?: return null

        val context = expression.analyze()
        val resolvedCall = expression.getResolvedCall(context) ?: return null
        val fqName = DescriptorUtils.getFqName(resolvedCall.resultingDescriptor).toString()
        if (fqName !in FOR_EACH_FQ_NAMES && fqName !in FOR_EACH_INDEXED_FQ_NAMES) return null

        if (expression.isUsedAsExpression(context)) return null

        val explicitReceiver = resolvedCall.call.explicitReceiver as? ExpressionReceiver
        val receiver = if (explicitReceiver != null) {
            explicitReceiver.expression
        } else {
            val scope = expression.getResolutionScope(context) ?: return null
            val implicitReceiverType = resolvedCall.getImplicitReceiverValue()?.type ?: return null
            val factory = scope.getFactoryForImplicitReceiverWithSubtypeOf(implicitReceiverType) ?: return null
            KtPsiFactory(nameExpr.project).createExpression(if (factory.isImmediate) "this" else factory.expressionText)
        }
        val argument = resolvedCall.call.valueArguments.singleOrNull() ?: return null
        val functionLiteral = argument.getArgumentExpression() as? KtLambdaExpression ?: return null
        return Data(expression, receiver, functionLiteral, context)
    }

    private fun generateLoop(
        functionLiteral: KtLambdaExpression,
        receiver: KtExpression,
        isForEachIndexed: Boolean,
        context: BindingContext
    ): KtExpression {
        val psiFactory = KtPsiFactory(functionLiteral.project)

        val body = functionLiteral.bodyExpression!!
        val function = functionLiteral.functionLiteral

        body.forEachDescendantOfType<KtReturnExpression> {
            if (it.getTargetFunction(context) == function) {
                it.replace(psiFactory.createExpression("continue"))
            }
        }

        val loopRange = KtPsiUtil.safeDeparenthesize(receiver)
        val parameters = functionLiteral.valueParameters
        val loop = if (isForEachIndexed) {
            val loopRangeWithIndex = if (loopRange is KtThisExpression && loopRange.labelQualifier == null) {
                psiFactory.createExpression("withIndex()")
            } else {
                psiFactory.createExpressionByPattern("$0.withIndex()", loopRange)
            }
            psiFactory.createExpressionByPattern(
                "for(($0, $1) in $2){ $3 }",
                parameters[0].text,
                parameters[1].text,
                loopRangeWithIndex,
                body.allChildren
            )
        } else {
            psiFactory.createExpressionByPattern(
                "for($0 in $1){ $2 }",
                parameters.singleOrNull() ?: "it",
                loopRange,
                body.allChildren
            )
        }

        return if (loopRange.getQualifiedExpressionForReceiver() is KtSafeQualifiedExpression) {
            psiFactory.createExpressionByPattern("if ($0 != null) { $1 }", loopRange, loop)
        } else {
            loop
        }
    }
}
