// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.coroutines

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.collections.AbstractCallChainChecker
import org.jetbrains.kotlin.idea.inspections.collections.AbstractCallChainChecker.Conversion
import org.jetbrains.kotlin.idea.inspections.collections.SimplifyCallChainFix
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

/**
 * Test - [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.Coroutines.RedundantRunCatching]
 */
class RedundantRunCatchingInspection : AbstractCallChainChecker() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        qualifiedExpressionVisitor(fun(expression) {
            val conversion = findQualifiedConversion(expression, conversionGroups) { _, _, _, _ -> true } ?: return
            val replacement = conversion.replacement
            val descriptor = holder.manager.createProblemDescriptor(
                expression,
                expression.firstCalleeExpression()!!.textRange.shiftRight(-expression.startOffset),
                KotlinBundle.message("redundant.runcatching.call.may.be.reduced.to.0", replacement),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                SimplifyCallChainFix(conversion)
            )
            holder.registerProblem(descriptor)
        })

    private val conversionGroups = conversions.group()
}

private val conversions: List<Conversion> = listOf(
    Conversion(
        "kotlin.runCatching", // FQNs are hardcoded instead of specifying their names via reflection because
        "kotlin.getOrThrow",  // referencing function which has generics isn't yet supported in Kotlin KT-12140
        "run"
    )
)
