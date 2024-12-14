// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiverSmartCastKind
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.HighlightingFactory
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

internal class KotlinExpressionsSmartcastSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession) : KotlinSemanticAnalyzer(holder, session) {
    override fun visitExpression(expression: KtExpression) {
        highlightExpression(expression)
    }

    @OptIn(KaExperimentalApi::class)
    private fun highlightExpression(expression: KtExpression): Unit = with(session) {
        expression.implicitReceiverSmartCasts.forEach {
            val receiverName = when (it.kind) {
                KaImplicitReceiverSmartCastKind.EXTENSION -> KotlinBaseHighlightingBundle.message("extension.implicit.receiver")
                KaImplicitReceiverSmartCastKind.DISPATCH -> KotlinBaseHighlightingBundle.message("implicit.receiver")
            }

            val builder = HighlightingFactory.highlightName(
                expression,
                KotlinHighlightInfoTypeSemanticNames.SMART_CAST_RECEIVER,
                KotlinBaseHighlightingBundle.message(
                    "0.smart.cast.to.1",
                    receiverName,
                    it.type.toString()
                )
            )
            if (builder != null) {
                holder.add(builder.create())
            }
        }
        expression.smartCastInfo?.takeIf { it.isStable }?.let { info ->
            val builder = HighlightingFactory.highlightName(
                getSmartCastTarget(expression),
                KotlinHighlightInfoTypeSemanticNames.SMART_CAST_VALUE,
                KotlinBaseHighlightingBundle.message(
                    "smart.cast.to.0",
                    info.smartCastType.render(position = Variance.INVARIANT)
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
