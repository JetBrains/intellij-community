// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ConvertConcatenationToBuildStringIntention : ConvertToStringTemplateIntention() {
    init {
        setTextGetter(KotlinBundle.lazyMessage("convert.concatenation.to.build.string"))
    }

    override fun isApplicableTo(element: KtBinaryExpression): Boolean {
        return super.isApplicableTo(element) && element.collectStringStringTemplateExpressions().all {
            ConvertStringTemplateToBuildStringIntention.canBeConvertedToBuildStringCall(it)
        }
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        ConvertStringTemplateToBuildStringIntention.convertToBuildStringCall(element, element.collectStringStringTemplateExpressions())
    }

    private fun KtBinaryExpression.collectStringStringTemplateExpressions(): List<KtStringTemplateExpression> {
        val stringTemplateExpressions = mutableListOf<KtStringTemplateExpression>()
        fun collect(expression: KtExpression?) {
            when (expression) {
                is KtBinaryExpression -> {
                    collect(expression.left)
                    collect(expression.right)
                }
                is KtStringTemplateExpression -> stringTemplateExpressions.add(expression)
            }
        }
        collect(this)
        return stringTemplateExpressions
    }
}
