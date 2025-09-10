// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isLetCallRedundant
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantLetCall
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertToIfNotNullExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.introduceValueForCondition
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class SafeAccessToIfThenIntention : SelfTargetingRangeIntention<KtSafeQualifiedExpression>(
    KtSafeQualifiedExpression::class.java, KotlinBundle.messagePointer("replace.safe.access.expression.with.if.expression")
), LowPriorityAction {
    override fun applicabilityRange(element: KtSafeQualifiedExpression): TextRange? {
        if (element.selectorExpression == null) return null
        return element.operationTokenNode.textRange
    }

    override fun startInWriteAction(): Boolean = false

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun applyTo(element: KtSafeQualifiedExpression, editor: Editor?) {
        val receiver = KtPsiUtil.safeDeparenthesize(element.receiverExpression)
        val selector = element.selectorExpression!!

        // introduce variable is required if expression is not pure
        val receiverIsPure = receiver.isPure()

        val elseBranchIsRedundant =
            allowAnalysisOnEdt { analyze(element) { !element.isUsedAsExpression } } ||
                    (element.parent as? KtBinaryExpression)?.operationToken == KtTokens.EQ

        val psiFactory = KtPsiFactory(element.project)
        val dotQualified = psiFactory.createExpressionByPattern("$0.$1", receiver, selector)
        val elseClause = if (elseBranchIsRedundant) null else psiFactory.createExpression("null")

        var ifExpression = element.convertToIfNotNullExpression(receiver, dotQualified, elseClause)

        val binaryExpression = (ifExpression.parent as? KtParenthesizedExpression)?.parent as? KtBinaryExpression
        var isAssignment = false
        val right = binaryExpression?.right
        if (right != null && binaryExpression.operationToken == KtTokens.EQ) {
            val replaced =
                runWriteAction { binaryExpression.replaced(psiFactory.createExpressionByPattern("$0 = $1", ifExpression.text, right)) }
            ifExpression = replaced.findDescendantOfType()!!
            isAssignment = true
        }

        val callExpression = (ifExpression.then as? KtQualifiedExpression)?.callExpression

        // can be calculated correctly only after if (x != null) check above
        val isLetCallRedundant =
            callExpression != null && allowAnalysisOnEdt { analyze(ifExpression) { isLetCallRedundant(callExpression) } }

        if (isLetCallRedundant) {
            runWriteAction {
                removeRedundantLetCall(callExpression!!) {
                    editor?.caretModel?.moveToOffset(it.textRange.startOffset)
                }
            }
        }

        if (!receiverIsPure) {
            val valueInThenBranch = when {
                isAssignment -> ((ifExpression.then as? KtBinaryExpression)?.left as? KtDotQualifiedExpression)?.receiverExpression

                isLetCallRedundant -> {
                    val propertyToCheck =
                        (ifExpression.condition as? KtBinaryExpression)?.left?.mainReference?.resolve() as? PsiNamedElement

                    val name = propertyToCheck?.name

                    ifExpression.then?.findDescendantOfType<KtNameReferenceExpression> {
                        it.getReferencedName() == name && it.mainReference.resolve() == propertyToCheck
                    }
                }

                else -> (ifExpression.then as? KtDotQualifiedExpression)?.receiverExpression
            }
            if (valueInThenBranch != null) ifExpression.introduceValueForCondition(valueInThenBranch, editor)
        }
    }
}