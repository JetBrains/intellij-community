// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter.visitors

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiverSmartCastKind
import org.jetbrains.kotlin.idea.KotlinIdeaAnalysisBundle
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.psi.*

internal class ExpressionsSmartcastHighlightingVisitor(
    analysisSession: KtAnalysisSession,
    holder: AnnotationHolder
) : FirAfterResolveHighlightingVisitor(analysisSession, holder) {
    override fun visitExpression(expression: KtExpression) = with(analysisSession) {
        expression.getImplicitReceiverSmartCast().forEach {
            val receiverName = when (it.kind) {
                KtImplicitReceiverSmartCastKind.EXTENSION -> KotlinIdeaAnalysisBundle.message("extension.implicit.receiver")
                KtImplicitReceiverSmartCastKind.DISPATCH -> KotlinIdeaAnalysisBundle.message("implicit.receiver")
            }

            createInfoAnnotation(
                expression,
                KotlinIdeaAnalysisBundle.message(
                    "0.smart.cast.to.1",
                    receiverName,
                    it.type.asStringForDebugging()
                ),
                KotlinHighlightingColors.SMART_CAST_RECEIVER
            )
        }
        expression.getSmartCastInfo()?.let { info ->
            createInfoAnnotation(
                getSmartCastTarget(expression),
                KotlinIdeaAnalysisBundle.message(
                    "smart.cast.to.0",
                    info.smartCastType.asStringForDebugging()
                ),
                KotlinHighlightingColors.SMART_CAST_VALUE
            )
        }

        super.visitExpression(expression)
    }
}


private fun getSmartCastTarget(expression: KtExpression): PsiElement {
    var target: PsiElement = expression
    if (target is KtParenthesizedExpression) {
        target = KtPsiUtil.deparenthesize(target) ?: expression
    }
    return when (target) {
        is KtIfExpression -> target.ifKeyword
        is KtWhenExpression -> target.whenKeyword
        is KtBinaryExpression -> target.operationReference
        else -> target
    }
}
