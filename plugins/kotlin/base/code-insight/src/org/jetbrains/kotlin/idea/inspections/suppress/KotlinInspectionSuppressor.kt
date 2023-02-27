// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.suppress

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.psi.findSingleLiteralStringTemplateText
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinInspectionSuppressor : InspectionSuppressor, RedundantSuppressionDetector {
    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
        element ?: return SuppressQuickFix.EMPTY_ARRAY
        return createSuppressWarningActions(element, Severity.WARNING, toolId).map {
            object : SuppressQuickFix {
                override fun getFamilyName() = it.familyName

                override fun getName() = it.text

                override fun applyFix(project: Project, descriptor: ProblemDescriptor) = it.invoke(project, null, descriptor.psiElement)

                override fun isAvailable(project: Project, context: PsiElement) = it.isAvailable(project, null, context)

                override fun isSuppressAll() = it.isSuppressAll
            }
        }.toTypedArray()
    }

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
        KotlinSuppressionChecker.getInstance().isSuppressedFor(element, toolId)

    override fun getSuppressionIds(element: PsiElement): String? = suppressionIds(element).ifNotEmpty { joinToString(separator = ",") }

    fun suppressionIds(element: PsiElement): List<String> {
        if (element !is KtAnnotated) {
            return emptyList()
        }

        return KotlinPsiHeuristics.findSuppressedEntities(element) ?: emptyList()
    }

    override fun createRemoveRedundantSuppressionFix(toolId: String): LocalQuickFix = RemoveRedundantSuppression(toolId)

    override fun isSuppressionFor(elementWithSuppression: PsiElement, place: PsiElement, toolId: String): Boolean {
        return elementWithSuppression === place || elementWithSuppression.isAncestor(place, false)
    }

    override fun getHighlightingRange(elementWithSuppression: PsiElement, toolId: String): TextRange? {
        val annotated = elementWithSuppression as? KtAnnotated ?: return null
        val suppressAnnotationEntry = KotlinPsiHeuristics.findSuppressAnnotation(annotated) ?: return null
        val index = StringUtil.indexOfIgnoreCase(suppressAnnotationEntry.text, toolId, 0).takeIf { it >= 0 } ?: return null
        return TextRange(index, index + toolId.length).shiftRight(suppressAnnotationEntry.textRangeIn(annotated).startOffset)
    }
}

private class RemoveRedundantSuppression(private val toolId: String) : LocalQuickFix {
    override fun getFamilyName(): String = QuickFixBundle.message("remove.suppression.action.family")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // descriptor.psiElement can be identifier in case of bunch mode. See [com.intellij.codeInspection.RedundantSuppressInspectionBase.checkElement]
        val annotated = descriptor.psiElement.parentOfType<KtAnnotated>(withSelf = true)
            ?: throw KotlinExceptionWithAttachments("Annotated element is not found")
                .withAttachment(name = "class.txt", content = descriptor.psiElement.javaClass)
                .withPsiAttachment("element.txt", descriptor.psiElement)

        val suppressAnnotationEntry = KotlinPsiHeuristics.findSuppressAnnotation(annotated)
            ?: throw KotlinExceptionWithAttachments("Suppress annotation is not found")
                .withPsiAttachment("element.txt", descriptor.psiElement)
                .withPsiAttachment("annotatedElement.txt", annotated)

        if (suppressAnnotationEntry.valueArguments.size == 1) {
            annotated.safeAs<KtAnnotatedExpression>()?.baseExpression?.let { annotated.replace(it) } ?: suppressAnnotationEntry.delete()
        } else {
            val valueArgumentList = suppressAnnotationEntry.valueArgumentList ?: return
            val argument = valueArgumentList.arguments.find {
                it.findSingleLiteralStringTemplateText()?.equals(toolId, ignoreCase = true) == true
            } ?: throw KotlinExceptionWithAttachments("ToolId is not found")
                .withAttachment("arguments.txt", valueArgumentList.text)
                .withAttachment("tool.txt", toolId)

            valueArgumentList.removeArgument(argument)
        }
    }
}