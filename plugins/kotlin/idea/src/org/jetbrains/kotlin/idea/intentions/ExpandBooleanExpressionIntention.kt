// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isBoolean

class ExpandBooleanExpressionIntention : SelfTargetingRangeIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.lazyMessage("expand.boolean.expression.to.if.else")
) {
    override fun applicabilityRange(element: KtExpression): TextRange? {
        if (!element.isTargetExpression() || element.parent.isTargetExpression()) return null
        if (element.deparenthesize() is KtConstantExpression) return null
        val parent = element.parent
        if (parent is KtValueArgument || parent is KtParameter || parent is KtStringTemplateEntry) return null
        val context = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
        if (context[BindingContext.EXPRESSION_TYPE_INFO, element]?.type?.isBoolean() != true) return null
        return element.textRange
    }

    private fun PsiElement.isTargetExpression() = this is KtSimpleNameExpression || this is KtCallExpression ||
            this is KtQualifiedExpression || this is KtOperationExpression || this is KtParenthesizedExpression

    override fun applyTo(element: KtExpression, editor: Editor?) {
        val ifExpression = KtPsiFactory(element).createExpressionByPattern("if ($0) {\ntrue\n} else {\nfalse\n}", element)
        val replaced = element.replace(ifExpression)
        if (replaced != null) {
            editor?.caretModel?.moveToOffset(replaced.startOffset)
        }
    }
}
