// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiverSmartCastKind
import org.jetbrains.kotlin.idea.base.highlighting.HighlightingFactory
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.psi.*

context(KtAnalysisSession)
internal class ExpressionsSmartcastHighlighter(holder: HighlightInfoHolder) : KotlinSemanticAnalyzer(holder) {
    override fun visitExpression(expression: KtExpression) {
        highlightExpression(expression)
    }

    private fun highlightExpression(expression: KtExpression) {
        expression.getImplicitReceiverSmartCast().forEach {
            val receiverName = when (it.kind) {
                KtImplicitReceiverSmartCastKind.EXTENSION -> KotlinBaseHighlightingBundle.message("extension.implicit.receiver")
                KtImplicitReceiverSmartCastKind.DISPATCH -> KotlinBaseHighlightingBundle.message("implicit.receiver")
            }

            val builder = HighlightingFactory.highlightName(
              expression,
              KotlinHighlightInfoTypeSemanticNames.SMART_CAST_RECEIVER,
              KotlinBaseHighlightingBundle.message(
                "0.smart.cast.to.1",
                receiverName,
                it.type.asStringForDebugging()
              )
            )
            if (builder != null) {
                holder.add(builder.create())
            }
        }
        expression.getSmartCastInfo()?.let { info ->
            val builder = HighlightingFactory.highlightName(
              getSmartCastTarget(expression),
              KotlinHighlightInfoTypeSemanticNames.SMART_CAST_VALUE,
              KotlinBaseHighlightingBundle.message(
                "smart.cast.to.0",
                info.smartCastType.asStringForDebugging()
              )
            )
            if (builder != null) {
                holder.add(builder.create())
            }
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
