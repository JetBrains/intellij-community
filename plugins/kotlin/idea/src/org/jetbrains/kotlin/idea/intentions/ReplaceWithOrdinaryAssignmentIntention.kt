// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceWithOrdinaryAssignmentIntention : SelfTargetingIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.lazyMessage("replace.with.ordinary.assignment")
), LowPriorityAction {
    override fun isApplicableTo(element: KtBinaryExpression, caretOffset: Int): Boolean {
        val operationReference = element.operationReference
        if (!operationReference.textRange.containsOffset(caretOffset)) return false
        if (element.operationToken !in KtTokens.AUGMENTED_ASSIGNMENTS) return false
        val left = element.left ?: return false
        if (left.safeAs<KtQualifiedExpression>()?.receiverExpression is KtQualifiedExpression) return false
        if (element.right == null) return false

        val resultingDescriptor = operationReference.resolveToCall(BodyResolveMode.PARTIAL)?.resultingDescriptor ?: return false
        return resultingDescriptor.name !in OperatorNameConventions.ASSIGNMENT_OPERATIONS
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val left = element.left!!
        val right = element.right!!
        val psiFactory = KtPsiFactory(element.project)

        val assignOpText = element.operationReference.text
        assert(assignOpText.endsWith("="))
        val operationText = assignOpText.substring(0, assignOpText.length - 1)

        element.replace(psiFactory.createExpressionByPattern("$0 = $0 $operationText $1", left, right))
    }
}
