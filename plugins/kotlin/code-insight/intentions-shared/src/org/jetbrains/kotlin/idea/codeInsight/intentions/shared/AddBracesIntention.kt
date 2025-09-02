// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.AddBracesUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.getControlFlowElementDescription
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@Internal
@IntellijInternalApi
class AddBracesIntention : SelfTargetingIntention<KtElement>(KtElement::class.java, KotlinBundle.lazyMessage("add.braces")) {
    override fun isApplicableTo(element: KtElement, caretOffset: Int): Boolean {
        val expression = element.getTargetExpression(caretOffset) ?: return false
        if (expression is KtBlockExpression) return false

        return when (val parent = expression.parent) {
            is KtContainerNode -> {
                val description = parent.getControlFlowElementDescription() ?: return false
                setTextGetter(KotlinBundle.lazyMessage("add.braces.to.0.statement", description))
                true
            }
            is KtWhenEntry -> {
                setTextGetter(KotlinBundle.lazyMessage("add.braces.to.when.entry"))
                true
            }
            else -> {
                false
            }
        }
    }

    override fun applyTo(element: KtElement, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")
        val expression = element.getTargetExpression(editor.caretModel.offset) ?: return
        AddBracesUtils.addBraces(element, expression)
    }

    private fun KtElement.getTargetExpression(caretLocation: Int): KtExpression? {
        return when (this) {
            is KtIfExpression -> {
                val thenExpr = then ?: return null
                val elseExpr = `else`
                if (elseExpr != null && caretLocation >= (elseKeyword?.startOffset ?: return null)) {
                    elseExpr
                } else {
                    thenExpr
                }
            }

            is KtLoopExpression -> body
            is KtWhenEntry -> expression
            else -> null
        }
    }
}
