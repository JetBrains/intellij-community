// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression

internal class KotlinDoubleNegationInspection :
    AbstractKotlinApplicatorBasedInspection<KtPrefixExpression, KotlinApplicatorInput.Empty>(KtPrefixExpression::class) {
    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    override fun getInputProvider() = inputProvider { element: KtPrefixExpression ->
        KotlinApplicatorInput.Empty.takeIf { element.getKtType()?.isBoolean == true }
    }

    override fun getApplicator() = applicator<KtPrefixExpression, KotlinApplicatorInput.Empty> {
        familyAndActionName(KotlinBundle.lazyMessage("inspection.kotlin.double.negation.display.name"))
        isApplicableByPsi { element ->
            element.operationToken == KtTokens.EXCL
                    && (element.parentThroughParenthesis as? KtPrefixExpression)?.operationToken == KtTokens.EXCL
        }
        applyTo { element, _ ->
            element.baseExpression?.let { element.parentThroughParenthesis.replace(it) }
        }
    }
}

private val PsiElement.parentThroughParenthesis: PsiElement
    get() {
        var result = parent
        while (result is KtParenthesizedExpression) {
            result = result.parent
        }
        return result
    }
