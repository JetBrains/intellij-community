// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertToIfNotNullExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertToIfStatement
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.introduceValueForCondition
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class ElvisToIfThenIntention : SelfTargetingRangeIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.messagePointer("replace.elvis.expression.with.if.expression")
), LowPriorityAction {
    override fun applicabilityRange(element: KtBinaryExpression): TextRange? =
        if (element.operationToken == KtTokens.ELVIS && element.left != null && element.right != null)
            element.operationReference.textRange
        else
            null

    class Context(val leftReceiver: KtBinaryExpressionWithTypeRHS?, val isNothingOnRightSide: Boolean)

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.elvis.expression.with.if.expression")

    context(_: KaSession)
    private fun KtExpression.findSafeCastReceiver(): KtBinaryExpressionWithTypeRHS? {
        var current = this
        while (current is KtQualifiedExpression) {
            val resolvedCall = current.selectorExpression?.resolveToCall() ?: return null
            val type = resolvedCall.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.signature?.returnType
            if (type != null && type.nullability == KaTypeNullability.NULLABLE) {
                return null
            }
            current = current.receiverExpression
        }

        current = KtPsiUtil.safeDeparenthesize(current)
        return (current as? KtBinaryExpressionWithTypeRHS)?.takeIf {
            it.operationReference.getReferencedNameElementType() === KtTokens.AS_SAFE && it.right != null
        }
    }

    private fun KtExpression.buildExpressionWithReplacedReceiver(
        factory: KtPsiFactory,
        newReceiver: KtExpression,
        topLevel: Boolean = true
    ): KtExpression {
        if (this !is KtQualifiedExpression) return newReceiver
        return factory.buildExpression(reformat = topLevel) {
            appendExpression(receiverExpression.buildExpressionWithReplacedReceiver(factory, newReceiver, topLevel = false))
            appendFixedText(".")
            appendExpression(selectorExpression)
        }
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val left = KtPsiUtil.safeDeparenthesize(element.left!!)
        val right = KtPsiUtil.safeDeparenthesize(element.right!!)

        val psiFactory = KtPsiFactory(element.project)

        val (leftSafeCastReceiver, isNothingOnRightSide) = analyzeInModalWindow(
            element,
            KotlinBundle.message("fix.change.signature.prepare")
        ) {
            val isNothingOnRightSide = KtPsiUtil.safeDeparenthesize(element.right!!).expressionType?.isNothingType == true
            left.findSafeCastReceiver() to isNothingOnRightSide
        }
        if (leftSafeCastReceiver == null) {
            val property = (KtPsiUtil.safeDeparenthesize(element).parent as? KtProperty)
            val propertyName = property?.name
            val rightIsReturnOrJumps = right is KtReturnExpression
                    || right is KtBreakExpression
                    || right is KtContinueExpression
                    || right is KtThrowExpression
                    || isNothingOnRightSide
            if (rightIsReturnOrJumps && propertyName != null) {
                val parent = property.parent
                runWriteAction {
                    parent.addAfter(psiFactory.createExpressionByPattern("if ($0 == null) $1", propertyName, right), property)
                    parent.addAfter(psiFactory.createNewLine(), property)
                    element.replace(left)
                }
                return
            }
        }

        val (leftIsStable, ifStatement) = if (leftSafeCastReceiver != null) {
            val newReceiver = leftSafeCastReceiver.left
            val typeReference = leftSafeCastReceiver.right!!
            newReceiver.isPure() to element.convertToIfStatement(
                psiFactory.createExpressionByPattern("$0 is $1", newReceiver, typeReference),
                left.buildExpressionWithReplacedReceiver(psiFactory, newReceiver),
                right
            )
        } else {
            left.isPure() to element.convertToIfNotNullExpression(left, left, right)
        }

        if (!leftIsStable) {
            ifStatement.introduceValueForCondition(ifStatement.then!!, editor)
        }
    }
}