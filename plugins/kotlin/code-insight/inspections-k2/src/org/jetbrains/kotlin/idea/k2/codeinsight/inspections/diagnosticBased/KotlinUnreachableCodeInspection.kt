// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinKtDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.reflect.KClass

class KotlinUnreachableCodeInspection : KotlinKtDiagnosticBasedInspectionBase<KtElement, KaFirDiagnostic.UnreachableCode, KotlinUnreachableCodeInspection.Context>() {
    data class Context(
        val unreachable: Collection<SmartPsiElementPointer<*>>
    )

    override val diagnosticType: KClass<KaFirDiagnostic.UnreachableCode>
        get() = KaFirDiagnostic.UnreachableCode::class

    override fun KaSession.prepareContextByDiagnostic(
        element: KtElement,
        diagnostic: KaFirDiagnostic.UnreachableCode
    ): Context? {
        val reachable = diagnostic.reachable
        val unreachable = diagnostic.unreachable
        val firstUnreachable = unreachable.firstOrNull()
        val unreachableElements = if (firstUnreachable != null) {
            val reachableTextRange = reachable.firstOrNull()
                ?.let { reachable.fold(it.textRange) { range: TextRange, element: PsiElement -> range.union(element.textRange) } }
                ?: return null

            val unreachableRange = unreachable.fold(firstUnreachable.textRange) { it, element -> it.union(element.textRange) }

            val startOffset = unreachableRange.startOffset
            val reachableStartOffset = reachableTextRange.startOffset

            val unreachableTextRange = if (startOffset <= reachableStartOffset) {
                TextRange(startOffset, unreachableRange.endOffset.coerceAtMost(reachableStartOffset))
            } else {
                TextRange(reachableStartOffset, unreachableRange.endOffset.coerceAtMost(reachableTextRange.endOffset))
            }

            calculateUnreachableElements(element, unreachableTextRange, unreachableRange)
        } else {
            emptyList()
        }
        return Context(unreachableElements)
    }

    private fun calculateUnreachableElements(
        element: KtElement,
        unreachableTextRange: TextRange,
        singleUnreachableRange: TextRange
    ): List<SmartPsiElementPointer<*>> = buildList {
        var offset = unreachableTextRange.startOffset - element.startOffset
        while (offset < singleUnreachableRange.endOffset - element.startOffset) {
            val e = element.findElementAt(offset) ?: break
            if (e !is PsiWhiteSpace && unreachableTextRange.contains(e.textRange)) {
                this += e.createSmartPointer()
            }
            offset += e.textRange.length
        }
    }

    override fun getProblemDescription(
        element: KtElement,
        context: Context
    ): @InspectionMessage String = KotlinBundle.message("inspection.unreachable.code")

    override fun createQuickFix(
        element: KtElement,
        context: Context
    ): KotlinModCommandQuickFix<KtElement> = object : KotlinModCommandQuickFix<KtElement>() {

        override fun getFamilyName(): String = KotlinBundle.message("inspection.unreachable.code.remove.unreachable.code")

        override fun applyFix(
            project: Project,
            element: KtElement,
            updater: ModPsiUpdater,
        ) {
            val unreachable = context.unreachable
            if (unreachable.isEmpty()) {
                element.delete()
            } else {
                unreachable.mapNotNull {
                    it.element?.let(updater::getWritable)
                }.forEach(PsiElement::delete)
            }
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitKtElement(element: KtElement) {
            visitTargetElement(element, holder, isOnTheFly)
        }
    }

    override fun registerProblem(
        ranges: List<TextRange>,
        holder: ProblemsHolder,
        element: KtElement,
        context: Context,
        isOnTheFly: Boolean
    ) {
        val unreachable = context.unreachable
        val first = unreachable.firstOrNull()?.element
        val last = unreachable.lastOrNull()?.element
        if (first == null || last == null) {
            val problemDescriptor = holder.manager.createProblemDescriptor(
                element,
                context,
                TextRange(0, element.textRange.length),
                isOnTheFly,
            )
            holder.registerProblem(problemDescriptor)
        } else {
            val problemDescriptor = holder.manager.createProblemDescriptor(
                element,
                context,
                first.textRange.union(last.textRange).shiftLeft(element.startOffset),
                isOnTheFly,
            )
            holder.registerProblem(problemDescriptor)
        }
    }
}