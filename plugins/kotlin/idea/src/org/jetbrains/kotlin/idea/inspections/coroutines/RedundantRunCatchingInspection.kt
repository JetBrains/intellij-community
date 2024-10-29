// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.coroutines

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainExpressions
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConversionId
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyCallChainFix
import org.jetbrains.kotlin.idea.inspections.collections.AbstractCallChainChecker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

/**
 * Test - [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.Coroutines.RedundantRunCatching]
 */
class RedundantRunCatchingInspection : AbstractCallChainChecker() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        qualifiedExpressionVisitor(fun(expression) {
            val callChainExpressions = CallChainExpressions.from(expression) ?: return
            val conversion = findQualifiedConversion(callChainExpressions, conversionGroups) { _, _, _, _ -> true } ?: return
            val replacement = conversion.replacement
            val descriptor = holder.manager.createProblemDescriptor(
                expression,
                callChainExpressions.firstCalleeExpression.textRange.shiftRight(-expression.startOffset),
                KotlinBundle.message("redundant.runcatching.call.may.be.reduced.to.0", replacement),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                SimplifyCallChainFix(conversion)
            )
            holder.registerProblem(descriptor)
        })

    private val conversionGroups: Map<ConversionId, List<CallChainConversion>> = conversions.groupBy { conversion -> conversion.id }
}

private val conversions: List<CallChainConversion> = listOf(
    CallChainConversion(
        FqName("kotlin.runCatching"), // FQNs are hardcoded instead of specifying their names via reflection because
        FqName("kotlin.getOrThrow"),  // referencing function which has generics isn't yet supported in Kotlin KT-12140
        "run"
    )
)
