// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicator
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.AbstractHLInspection
import org.jetbrains.kotlin.idea.fir.api.applicator.*
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression

class HLDoubleNegationInspection : AbstractHLInspection<KtPrefixExpression, HLApplicatorInput.Empty>(KtPrefixExpression::class) {

    override val applicabilityRange: HLApplicabilityRange<KtPrefixExpression> = ApplicabilityRanges.SELF

    override val presentation: HLPresentation<KtPrefixExpression> =
        presentation {
           highlightType(ProblemHighlightType.WEAK_WARNING)
        }

    override val inputProvider: HLApplicatorInputProvider<KtPrefixExpression, HLApplicatorInput.Empty> =
        inputProvider { element ->
            HLApplicatorInput.Empty.takeIf { element.getKtType()?.isBoolean == true }
        }

    override val applicator: HLApplicator<KtPrefixExpression, HLApplicatorInput.Empty> =
        applicator {
            familyAndActionName(KotlinBundle.lazyMessage("inspection.kotlin.double.negation.display.name"))
            isApplicableByPsi { element ->
                element.operationToken == KtTokens.EXCL && (element.parentThroughParenthesis as? KtPrefixExpression)?.operationToken == KtTokens.EXCL
            }
            applyTo { element, _ ->
                element.baseExpression?.let { element.parentThroughParenthesis.replace(it) }
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
}

