// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiverSmartCastKind
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.psi.*

internal class ExpressionsSmartcastHighlighter(
  project: Project,
) : AfterResolveHighlighter(project) {

    context(KtAnalysisSession)
    override fun highlight(element: KtElement): List<HighlightInfo.Builder> {
        return when (element) {
            is KtExpression -> highlightExpression(element)
            else -> emptyList()
        }
    }

    context(KtAnalysisSession)
    private fun highlightExpression(expression: KtExpression): List<HighlightInfo.Builder> {
        val result = mutableListOf<HighlightInfo.Builder>()
        expression.getImplicitReceiverSmartCast().forEach {
            val receiverName = when (it.kind) {
                KtImplicitReceiverSmartCastKind.EXTENSION -> KotlinBaseHighlightingBundle.message("extension.implicit.receiver")
                KtImplicitReceiverSmartCastKind.DISPATCH -> KotlinBaseHighlightingBundle.message("implicit.receiver")
            }

            result.add(createInfoAnnotation(
                expression,
                KotlinBaseHighlightingBundle.message(
                    "0.smart.cast.to.1",
                    receiverName,
                    it.type.asStringForDebugging()
                ),
                KotlinHighlightingColors.SMART_CAST_RECEIVER
            ))
        }
        expression.getSmartCastInfo()?.let { info ->
            result.add(createInfoAnnotation(
                getSmartCastTarget(expression),
                KotlinBaseHighlightingBundle.message(
                    "smart.cast.to.0",
                    info.smartCastType.asStringForDebugging()
                ),
                KotlinHighlightingColors.SMART_CAST_VALUE
            ))
        }
        return result
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
