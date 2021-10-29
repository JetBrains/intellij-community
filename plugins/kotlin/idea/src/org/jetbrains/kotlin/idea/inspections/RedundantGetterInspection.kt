// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.util.isRedundantGetter
import org.jetbrains.kotlin.idea.util.removeRedundantGetter
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.propertyAccessorVisitor
import org.jetbrains.kotlin.psi.psiUtil.startOffset

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
