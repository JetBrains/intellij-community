// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyzeWithReadAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.reflect.KClass

@Deprecated("Please don't use this for new inspections. Use `KotlinApplicableInspection` or `KotlinApplicableInspectionWithContext` instead.")
abstract class AbstractKotlinApplicatorBasedInspection<PSI : KtElement, INPUT : KotlinApplicatorInput>(
    val elementType: KClass<PSI>
) : AbstractKotlinInspection() {
    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)

                if (!elementType.isInstance(element) || element.textLength == 0) return
                @Suppress("UNCHECKED_CAST")
                visitTargetElement(element as PSI, holder, isOnTheFly)
            }
        }

    private fun visitTargetElement(element: PSI, holder: ProblemsHolder, isOnTheFly: Boolean) {
        val applicator = getApplicator()
        if (!applicator.isApplicableByPsi(element, holder.project)) return
        val targets = getApplicabilityRange().getApplicabilityRanges(element)
        if (targets.isEmpty()) return

        val input = getInput(element) ?: return
        require(input.isValidFor(element)) { "Input should be valid after creation" }

        registerProblems(applicator, holder, element, targets, isOnTheFly, input)
    }


    private fun registerProblems(
        applicator: KotlinApplicator<PSI, INPUT>,
        holder: ProblemsHolder,
        element: PSI,
        ranges: List<TextRange>,
        isOnTheFly: Boolean,
        input: INPUT
    ) {
        val description = applicator.getActionName(element, input)
        val fix = applicator.asLocalQuickFix(input, actionName = applicator.getActionName(element, input))

        ranges.forEach { range ->
            registerProblem(holder, element, range, description, isOnTheFly, fix)
        }
    }

    private fun registerProblem(
        holder: ProblemsHolder,
        element: PSI,
        range: TextRange,
        @InspectionMessage description: String,
        isOnTheFly: Boolean,
        fix: LocalQuickFix
    ) {
        with(holder) {
            val problemDescriptor = manager.createProblemDescriptor(
                element,
                range,
                description,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                fix
            )
            registerProblem(problemDescriptor)
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun getInput(element: PSI): INPUT? = allowAnalysisOnEdt {
        analyzeWithReadAction(element) {
            with(getInputProvider()) { provideInput(element) }
        }
    }


    abstract fun getApplicabilityRange(): KotlinApplicabilityRange<PSI>
    abstract fun getInputProvider(): KotlinApplicatorInputProvider<PSI, INPUT>
    abstract fun getApplicator(): KotlinApplicator<PSI, INPUT>
}

private fun <PSI : PsiElement, INPUT : KotlinApplicatorInput> KotlinApplicator<PSI, INPUT>.asLocalQuickFix(
    input: INPUT,
    @IntentionName actionName: String,
): LocalQuickFix = object : LocalQuickFix {
    override fun startInWriteAction() = false

    override fun getElementToMakeWritable(currentFile: PsiFile) = currentFile

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        @Suppress("UNCHECKED_CAST")
        val element = descriptor.psiElement as PSI

        if (isApplicableByPsi(element, project) && input.isValidFor(element)) {
             runWriteActionIfPhysical(element) {
                applyTo(element, input, project, element.findExistingEditor())
            }
        }
    }

    override fun getFamilyName() = this@asLocalQuickFix.getFamilyName()
    override fun getName() = actionName
}
