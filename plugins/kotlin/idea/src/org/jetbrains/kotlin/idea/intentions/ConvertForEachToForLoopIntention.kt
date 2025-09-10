// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
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

class ConvertForEachToForLoopIntention : SelfTargetingOffsetIndependentIntention<KtSimpleNameExpression>(
    KtSimpleNameExpression::class.java, KotlinBundle.messagePointer("replace.with.a.for.loop")
) {
    override fun isApplicableTo(element: KtSimpleNameExpression): Boolean {
        val referencedName = element.getReferencedName()
        val isForEach = referencedName == FOR_EACH_NAME
        val isForEachIndexed = referencedName == FOR_EACH_INDEXED_NAME
        if (!isForEach && !isForEachIndexed) return false

        val data = extractData(element) ?: return false
        if (data.expressionToReplace is KtSafeQualifiedExpression && data.receiver !is KtNameReferenceExpression) return false

        val valueParameterSize = data.functionLiteral.valueParameters.size
        if (isForEach && valueParameterSize > 1 || isForEachIndexed && valueParameterSize != 2) return false
        if (data.functionLiteral.bodyExpression == null) return false

        return true
    }

    override fun applyTo(element: KtSimpleNameExpression, editor: Editor?) {
        val (expressionToReplace, receiver, functionLiteral, context) = extractData(element)!!

        val commentSaver = CommentSaver(expressionToReplace)
        val project = element.project

        val isForEachIndexed = element.getReferencedName() == FOR_EACH_INDEXED_NAME
        val loop = generateLoop(functionLiteral, receiver, isForEachIndexed, context)
        val result = expressionToReplace.replace(loop)

        if (editor != null) {
            if (result is KtLabeledExpression) {
                editor.caretModel.moveToOffset(result.startOffset)
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
                KotlinVariableInplaceRenameHandler().doRename(result, editor, null)
            } else {
                val forExpression = result as? KtForExpression ?: result.collectDescendantsOfType<KtForExpression>().first()
                forExpression.loopParameter?.also { editor.caretModel.moveToOffset(it.startOffset) }
            }
        }

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

        val loopLabelName = KotlinNameSuggester.suggestNameByName("loop") { candidate ->
            !functionLiteral.anyDescendantOfType<KtLabeledExpression> { it.getLabelName() == candidate }
        }
        var needLoopLabel = false

        body.forEachDescendantOfType<KtReturnExpression> {
            if (it.getTargetFunction(context) == function) {
                val parentLoop = it.getStrictParentOfType<KtLoopExpression>()
                if (parentLoop?.getStrictParentOfType<KtLambdaExpression>() == functionLiteral) {
                    it.replace(psiFactory.createExpression("continue@$loopLabelName"))
                    needLoopLabel = true
                } else {
                    it.replace(psiFactory.createExpression("continue"))
                }
            }
        }

        val loopRange = KtPsiUtil.safeDeparenthesize(receiver)
        val parameters = functionLiteral.valueParameters
        val loopLabel = if (needLoopLabel) "$loopLabelName@ " else ""
        val loop = if (isForEachIndexed) {
            val loopRangeWithIndex = if (loopRange is KtThisExpression && loopRange.labelQualifier == null) {
                psiFactory.createExpression("withIndex()")
            } else {
                psiFactory.createExpressionByPattern("$0.withIndex()", loopRange)
            }
            psiFactory.createExpressionByPattern(
                "${loopLabel}for(($0, $1) in $2){ $3 }",
                parameters[0].text,
                parameters[1].text,
                loopRangeWithIndex,
                body.allChildren
            )
        } else {
            psiFactory.createExpressionByPattern(
                "${loopLabel}for($0 in $1){ $2 }",
                parameters.singleOrNull() ?: StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier,
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

private const val FOR_EACH_NAME = "forEach"
private val FOR_EACH_FQ_NAMES: Set<String> by lazy {
    sequenceOf("collections", "sequences", "text", "ranges").map { "kotlin.$it.$FOR_EACH_NAME" }.toSet()
}

private const val FOR_EACH_INDEXED_NAME = "forEachIndexed"
private val FOR_EACH_INDEXED_FQ_NAMES: Set<String> by lazy {
    sequenceOf("collections", "sequences", "text", "ranges").map { "kotlin.$it.$FOR_EACH_INDEXED_NAME" }.toSet()
}