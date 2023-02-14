// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.calls.util.getType

private fun KtStringTemplateExpression.singleExpressionOrNull() = children.singleOrNull()?.children?.firstOrNull() as? KtExpression

class RemoveSingleExpressionStringTemplateInspection : IntentionBasedInspection<KtStringTemplateExpression>(
    RemoveSingleExpressionStringTemplateIntention::class,
    additionalChecker = { templateExpression ->
        templateExpression.singleExpressionOrNull()?.let {
            KotlinBuiltIns.isString(it.getType(it.analyze()))
        } ?: false
    }
) {
    override val problemText = KotlinBundle.message("redundant.string.template")
}

class RemoveSingleExpressionStringTemplateIntention : SelfTargetingOffsetIndependentIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
    KotlinBundle.lazyMessage("remove.single.expression.string.template")
) {
    override fun isApplicableTo(element: KtStringTemplateExpression) = element.singleExpressionOrNull() != null

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val expression = element.singleExpressionOrNull() ?: return
        val type = expression.getType(expression.analyze())
        val newElement = if (KotlinBuiltIns.isString(type))
            expression
        else
            KtPsiFactory(element.project).createExpressionByPattern("$0.$1()", expression, "toString")

        element.replace(newElement)
    }
}