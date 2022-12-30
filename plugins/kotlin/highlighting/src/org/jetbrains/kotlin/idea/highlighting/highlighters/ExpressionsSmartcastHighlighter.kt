// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiverSmartCastKind
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.psi.*

internal class ExpressionsSmartcastHighlighter(
    holder: AnnotationHolder,
    project: Project,
) : AfterResolveHighlighter(holder, project) {

    context(KtAnalysisSession)
    override fun highlight(element: KtElement) {
        when (element) {
            is KtExpression -> higlightExpression(element)
            else -> {}
        }
    }

    context(KtAnalysisSession)
    private fun higlightExpression(expression: KtExpression) {
        expression.getImplicitReceiverSmartCast().forEach {
            val receiverName = when (it.kind) {
                KtImplicitReceiverSmartCastKind.EXTENSION -> KotlinBaseHighlightingBundle.message("extension.implicit.receiver")
                KtImplicitReceiverSmartCastKind.DISPATCH -> KotlinBaseHighlightingBundle.message("implicit.receiver")
            }

            createInfoAnnotation(
                expression,
                KotlinBaseHighlightingBundle.message(
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
                KotlinBaseHighlightingBundle.message(
                    "smart.cast.to.0",
                    info.smartCastType.asStringForDebugging()
                ),
                KotlinHighlightingColors.SMART_CAST_VALUE
            )
        }

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
