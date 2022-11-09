// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsight.api.inspections.KotlinSingleElementInspection
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * [KotlinApplicableInspectionBase] is a base implementation for [AbstractKotlinApplicableInspection] and
 * [AbstractKotlinApplicableInspectionWithContext].
 */
sealed class KotlinApplicableInspectionBase<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
) : KotlinSingleElementInspection<ELEMENT>(elementType), KotlinApplicableToolBase<ELEMENT> {
    internal class ProblemInfo(
        val description: @InspectionMessage String,
        val highlightType: ProblemHighlightType,
        val quickFix: LocalQuickFix,
    )

    /**
     * Builds a [ProblemInfo] instance for an element that this inspection is proven to be applicable to by PSI and applicability ranges.
     * The [ProblemInfo] will be used to register an appropriate problem with a quick fix.
     */
    internal abstract fun buildProblemInfo(element: ELEMENT): ProblemInfo?

    final override fun visitTargetElement(element: ELEMENT, holder: ProblemsHolder, isOnTheFly: Boolean) {
        if (!isApplicableByPsi(element)) return
        val ranges = getApplicabilityRange().getApplicabilityRanges(element)
        if (ranges.isEmpty()) return

        val problemInfo = buildProblemInfo(element) ?: return
        ranges.forEach { range ->
            with(holder) {
                registerProblem(
                    manager.createProblemDescriptor(
                        element,
                        range,
                        problemInfo.description,
                        problemInfo.highlightType,
                        isOnTheFly,
                        problemInfo.quickFix
                    )
                )
            }
        }
    }
}

internal abstract class KotlinApplicableInspectionQuickFix<ELEMENT : KtElement> : LocalQuickFix {
    abstract fun applyTo(element: ELEMENT)

    abstract override fun getName(): String

    final override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        @Suppress("UNCHECKED_CAST")
        val element = descriptor.psiElement as ELEMENT
        runWriteActionIfPhysical(element) {
            applyTo(element)
        }
    }

    final override fun startInWriteAction() = false

    final override fun getElementToMakeWritable(currentFile: PsiFile) = currentFile
}