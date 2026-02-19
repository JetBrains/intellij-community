// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.conventionNameCalls

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.calleeName
import org.jetbrains.kotlin.idea.intentions.isReceiverExpressionWithValue
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.types.expressions.OperatorConventions

internal class ReplaceCallWithUnaryOperatorIntention : SelfTargetingRangeIntention<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java,
    KotlinBundle.messagePointer("replace.call.with.unary.operator")
), HighPriorityAction {
    override fun applicabilityRange(element: KtDotQualifiedExpression): TextRange? {
        val operation = operation(element.calleeName) ?: return null
        if (!isApplicableOperation(operation)) return null

        val call = element.callExpression ?: return null
        if (call.typeArgumentList != null) return null
        if (call.valueArguments.isNotEmpty()) return null

        if (!element.isReceiverExpressionWithValue()) return null

        setTextGetter(KotlinBundle.messagePointer("replace.with.0.operator", operation.value))
        return call.calleeExpression?.textRange
    }

    override fun applyTo(element: KtDotQualifiedExpression, editor: Editor?) {
        val operation = operation(element.calleeName)?.value ?: return
        val receiver = element.receiverExpression
        element.replace(KtPsiFactory(element.project).createExpressionByPattern("$0$1", operation, receiver))
    }

    private fun isApplicableOperation(operation: KtSingleValueToken): Boolean = operation !in OperatorConventions.INCREMENT_OPERATIONS

    private fun operation(functionName: String?): KtSingleValueToken? = functionName?.let {
        OperatorConventions.UNARY_OPERATION_NAMES.inverse()[Name.identifier(it)]
    }
}
