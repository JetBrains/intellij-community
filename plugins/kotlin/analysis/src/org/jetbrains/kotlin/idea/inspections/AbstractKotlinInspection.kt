// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.unwrapped

abstract class AbstractKotlinInspection : LocalInspectionTool() {
    fun ProblemsHolder.registerProblemWithoutOfflineInformation(
        element: PsiElement,
        @InspectionMessage description: String,
        isOnTheFly: Boolean,
        highlightType: ProblemHighlightType,
        vararg fixes: LocalQuickFix
    ) {
        registerProblemWithoutOfflineInformation(element, description, isOnTheFly, highlightType, null, *fixes)
    }

    fun ProblemsHolder.registerProblemWithoutOfflineInformation(
        element: PsiElement,
        @InspectionMessage description: String,
        isOnTheFly: Boolean,
        highlightType: ProblemHighlightType,
        range: TextRange?,
        vararg fixes: LocalQuickFix
    ) {
        if (!isOnTheFly && highlightType == ProblemHighlightType.INFORMATION) return
        val problemDescriptor = manager.createProblemDescriptor(element, range, description, highlightType, isOnTheFly, *fixes)
        registerProblem(problemDescriptor)
    }
}

@Suppress("unused")
fun Array<ProblemDescriptor>.registerWithElementsUnwrapped(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    quickFixSubstitutor: ((LocalQuickFix, PsiElement) -> LocalQuickFix?)? = null
) {
    forEach { problem ->
        @Suppress("UNCHECKED_CAST")
        val originalFixes = problem.fixes as? Array<LocalQuickFix> ?: LocalQuickFix.EMPTY_ARRAY
        val newElement = problem.psiElement.unwrapped ?: return@forEach
        val newFixes = quickFixSubstitutor?.let { subst ->
            originalFixes.mapNotNull { subst(it, newElement) }.toTypedArray()
        } ?: originalFixes
        val descriptor =
            holder.manager.createProblemDescriptor(newElement, problem.descriptionTemplate, isOnTheFly, newFixes, problem.highlightType)
        holder.registerProblem(descriptor)
    }
}
