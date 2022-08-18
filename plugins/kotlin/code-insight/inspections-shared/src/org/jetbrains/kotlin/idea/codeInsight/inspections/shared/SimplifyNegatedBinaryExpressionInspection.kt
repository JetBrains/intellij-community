// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.canBeSimplified
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.negate
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.simplify
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.psi.*

class SimplifyNegatedBinaryExpressionInspection : AbstractApplicabilityBasedInspection<KtPrefixExpression>(KtPrefixExpression::class.java) {

    override fun inspectionHighlightRangeInElement(element: KtPrefixExpression) = element.operationReference.textRangeIn(element)

    override fun inspectionText(element: KtPrefixExpression) = KotlinBundle.message("negated.operation.should.be.simplified")

    override val defaultFixText get() = KotlinBundle.message("simplify.negated.operation")

    override fun fixText(element: KtPrefixExpression): String {
        val expression = KtPsiUtil.deparenthesize(element.baseExpression) as? KtOperationExpression ?: return defaultFixText
        val operation = expression.operationReference.getReferencedNameElementType() as? KtSingleValueToken ?: return defaultFixText
        val negatedOperation = operation.negate() ?: return defaultFixText
        return KotlinBundle.message("replace.negated.0.operation.with.1", operation.value, negatedOperation.value)
    }

    override fun isApplicable(element: KtPrefixExpression): Boolean {
        return element.canBeSimplified()
    }

    override fun applyTo(element: KtPrefixExpression, project: Project, editor: Editor?) {
        element.simplify()
    }
}