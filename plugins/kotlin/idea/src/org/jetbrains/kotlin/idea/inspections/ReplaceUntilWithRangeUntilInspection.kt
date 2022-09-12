// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType.*
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.EXPERIMENTAL_STDLIB_API_ANNOTATION
import org.jetbrains.kotlin.nj2k.areKotlinVersionsSufficientToUseRangeUntil
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker.Companion.isOptInAllowed

/**
 * Tests:
 * [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.ReplaceUntilWithRangeUntil]
 */
class ReplaceUntilWithRangeUntilInspection : AbstractRangeInspection() {
    override fun visitRange(range: KtExpression, context: Lazy<BindingContext>, type: RangeKtExpressionType, holder: ProblemsHolder) {
        when (type) {
            RANGE_TO, RANGE_UNTIL, DOWN_TO -> return
            UNTIL -> Unit
        }
        if (!range.isPossibleToUseRangeUntil(context)) return
        holder.registerProblem(
            range,
            KotlinBundle.message("until.can.be.replaced.with.rangeUntil.operator"),
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

    companion object {
        @ApiStatus.Internal
        fun KtElement.isPossibleToUseRangeUntil(context: Lazy<BindingContext>?): Boolean {
            val annotationFqName = FqName(EXPERIMENTAL_STDLIB_API_ANNOTATION)
            val languageVersionSettings = languageVersionSettings
            return module?.let { languageVersionSettings.areKotlinVersionsSufficientToUseRangeUntil(it, project) } == true &&
                    context?.let { isOptInAllowed(annotationFqName, languageVersionSettings, it.value) } == true
        }
    }
}
