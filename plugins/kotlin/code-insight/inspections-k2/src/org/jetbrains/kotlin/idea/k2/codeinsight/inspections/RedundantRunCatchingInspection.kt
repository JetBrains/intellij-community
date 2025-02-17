// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainExpressions
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyCallChainFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

internal class RedundantRunCatchingInspection : KotlinApplicableInspectionBase.Simple<KtQualifiedExpression, CallChainExpressions>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = qualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    private val conversion = CallChainConversion(
        FqName("kotlin.runCatching"), // FQNs are hardcoded instead of specifying their names via reflection because
        FqName("kotlin.getOrThrow"),  // referencing function which has generics isn't yet supported in Kotlin KT-12140
        "run"
    )

    override fun getProblemDescription(element: KtQualifiedExpression, context: CallChainExpressions): String =
        KotlinBundle.message("redundant.runcatching.call.may.be.reduced.to.0", conversion.replacement)

    override fun createQuickFix(
        element: KtQualifiedExpression,
        context: CallChainExpressions
    ): KotlinModCommandQuickFix<KtQualifiedExpression> = SimplifyCallChainFix(conversion)

    override fun getApplicableRanges(element: KtQualifiedExpression): List<TextRange> {
        val chain = CallChainExpressions.from(element) ?: return emptyList()
        return listOf(chain.firstCalleeExpression.textRange.shiftRight(-element.startOffset))
    }

    override fun isApplicableByPsi(element: KtQualifiedExpression): Boolean {
        // We calculate the CallChainExpressions several times because we cannot pass it between these stages.
        // Calculating the CallChainExpressions is very fast, so it is much preferable to do it before analysis.
        val callChainExpressions = CallChainExpressions.from(element) ?: return false
        if (callChainExpressions.firstCalleeExpression.text != "runCatching") return false
        if (callChainExpressions.secondCalleeExpression.text != "getOrThrow") return false

        // Do not apply for lambdas with return inside
        val lambdaArgument = callChainExpressions.firstCallExpression.lambdaArguments.firstOrNull()
        return lambdaArgument?.anyDescendantOfType<KtReturnExpression>() != true
    }

    override fun KaSession.prepareContext(element: KtQualifiedExpression): CallChainExpressions? {
        val callChainExpressions = CallChainExpressions.from(element) ?: return null
        val firstCalleeCall = callChainExpressions.firstCalleeExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val secondCalleeCall = callChainExpressions.secondCalleeExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        if (firstCalleeCall.partiallyAppliedSymbol.signature.callableId?.asSingleFqName() != conversion.firstFqName) {
            return null
        }
        if (secondCalleeCall.partiallyAppliedSymbol.signature.callableId?.asSingleFqName() != conversion.secondFqName) {
            return null
        }

        return callChainExpressions
    }
}