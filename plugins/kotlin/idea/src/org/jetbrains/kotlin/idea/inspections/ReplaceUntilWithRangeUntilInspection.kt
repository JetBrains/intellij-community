// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType.*
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.nj2k.isPossibleToUseRangeUntil
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.BindingContext

class ReplaceUntilWithRangeUntilInspection : AbstractRangeInspection() {
    override fun visitRange(range: KtExpression, context: Lazy<BindingContext>, type: RangeKtExpressionType, holder: ProblemsHolder) {
        when (type) {
            RANGE_TO, RANGE_UNTIL, DOWN_TO -> return
            UNTIL -> Unit
        }
        if (!range.isPossibleToUseRangeUntil(context)) return
        holder.registerProblem(
            range,
            KotlinBundle.message("until.is.an.old.way.replace.with.rangeUntil.operator"),
            ReplaceFix()
        )
    }

    private class ReplaceFix : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("replace.with.0.operator", "..<")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            val (left, right) = element.getArguments() ?: return
            if (left == null || right == null) return
            element.replace(KtPsiFactory(element).createExpressionByPattern("$0..<$1", left, right))
        }
    }
}
