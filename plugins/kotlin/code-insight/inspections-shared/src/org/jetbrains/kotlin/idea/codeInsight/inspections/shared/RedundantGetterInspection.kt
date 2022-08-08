// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.propertyAccessorVisitor
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantGetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantGetter

class RedundantGetterInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return propertyAccessorVisitor { accessor ->
            val rangeInElement = accessor.namePlaceholder.textRange?.shiftRight(-accessor.startOffset) ?: return@propertyAccessorVisitor
            if (accessor.isRedundantGetter()) {
                holder.registerProblem(
                    accessor,
                    KotlinBundle.message("redundant.getter"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    rangeInElement,
                    RemoveRedundantGetterFix()
                )
            }
        }
    }
}

class RemoveRedundantGetterFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.redundant.getter.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val accessor = descriptor.psiElement as? KtPropertyAccessor ?: return
        removeRedundantGetter(accessor)
    }
}