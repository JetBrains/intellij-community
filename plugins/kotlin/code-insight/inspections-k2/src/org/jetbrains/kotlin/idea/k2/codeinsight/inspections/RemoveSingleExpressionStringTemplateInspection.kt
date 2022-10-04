// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

class RemoveSingleExpressionStringTemplateInspection() :
    AbstractKotlinApplicatorBasedInspection<KtStringTemplateExpression, RemoveSingleExpressionStringTemplateInspection.Input>(
        KtStringTemplateExpression::class
    ) {
    data class Input(val isString: Boolean) : KotlinApplicatorInput

    override fun getApplicator(): KotlinApplicator<KtStringTemplateExpression, Input> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("remove.single.expression.string.template"))
        isApplicableByPsi { stringTemplateExpression: KtStringTemplateExpression ->
            stringTemplateExpression.singleExpressionOrNull() != null
        }
        applyTo { stringTemplateExpression, input ->
            // Note that we do not reuse the result of `stringTemplateExpression.singleExpressionOrNull()`
            // from `getInputProvider()` method because PsiElement may become invalidated between read actions
            // e.g., it may be reparsed and recreated and old reference will become stale and invalid.
            val expression = stringTemplateExpression.singleExpressionOrNull() ?: return@applyTo

            val newElement = if (input.isString) {
                expression
            } else {
                KtPsiFactory(stringTemplateExpression.project).createExpressionByPattern(
                    pattern = "$0.$1()", expression, "toString"
                )
            }
            stringTemplateExpression.replace(newElement)
        }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtStringTemplateExpression> = ApplicabilityRanges.SELF

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtStringTemplateExpression, Input> =
        inputProvider { stringTemplateExpression: KtStringTemplateExpression ->
            val expression = stringTemplateExpression.singleExpressionOrNull() ?: return@inputProvider null
            Input(expression.getKtType()?.isString == true)
        }

    private fun KtStringTemplateExpression.singleExpressionOrNull() = children.singleOrNull()?.children?.firstOrNull() as? KtExpression
}