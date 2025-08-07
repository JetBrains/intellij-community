// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

private const val COPY_METHOD_NAME = "copy"

internal class UnusedDataClassCopyResultInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = callExpressionVisitor { call: KtCallExpression ->
        val callee = call.calleeExpression ?: return@callExpressionVisitor
        if (callee.text != COPY_METHOD_NAME) return@callExpressionVisitor

        analyze(callee) {
            val resolvedCall = call.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze
            val receiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver ?: return@analyze
            val classSymbol = receiver.type.symbol as? KaNamedClassSymbol ?: return@analyze

            if (!classSymbol.isData) return@analyze

            if (call.getQualifiedExpressionForSelectorOrThis().isUsedAsExpression) return@analyze
            holder.registerProblem(callee, KotlinBundle.message("inspection.unused.result.of.data.class.copy"))
        }
    }
}
