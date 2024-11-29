// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ConvertVariableAssignmentToExpressionIntention : SelfTargetingIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.lazyMessage("convert.to.assignment.expression"),
) {
    override fun isApplicableTo(element: KtBinaryExpression, caretOffset: Int): Boolean =
        element.operationToken == KtTokens.EQ

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val left = element.left ?: return
        val right = element.right ?: return
        val newElement = KtPsiFactory(element.project).createExpressionByPattern("$0.also { $1 = it }", right, left)
        element.replace(newElement)
    }
}
