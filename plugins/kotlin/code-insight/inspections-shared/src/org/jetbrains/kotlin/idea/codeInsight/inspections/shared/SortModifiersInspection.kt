// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtContextParameterList
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.addRemoveModifier.sortModifiers
import org.jetbrains.kotlin.psi.modifierListVisitor
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.before
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@ApiStatus.Internal
@IntellijInternalApi
class SortModifiersInspection : KotlinApplicableInspectionBase<KtModifierList, SortModifiersInspection.Context>(),
    CleanupLocalInspectionTool {

    data class Context(
        val modifiersInWrongOrder: Boolean = false, //visibility/modality modifiers
        val modifiersNotAtTheEnd: Boolean = false, // they precede annotations or context parameters
        val contextParametersBeforeAnnotations: Boolean = false,
    )

    override fun KaSession.prepareContext(element: KtModifierList): Context {
        val modifierTokens = element.modifierKeywordTokens()
        return Context(
            modifiersInWrongOrder = modifierTokens.isNotEmpty() && modifierTokens != sortModifiers(modifierTokens),
            modifiersNotAtTheEnd = element.modifiersNotAtTheLastPlace(),
            contextParametersBeforeAnnotations = element.contextParametersBeforeAnnotations()
        )
    }

    override fun isApplicableByPsi(element: KtModifierList): Boolean {
        val modifierTokens = element.modifierKeywordTokens()
        val modifiersInWrongOrder = modifierTokens.isNotEmpty() && modifierTokens != sortModifiers(modifierTokens)
        val modifiersNotAtTheEnd = element.modifiersNotAtTheLastPlace()
        val contextParametersBeforeAnnotations = element.contextParametersBeforeAnnotations()
        return modifiersInWrongOrder || modifiersNotAtTheEnd || contextParametersBeforeAnnotations
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtModifierList,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val message =
            if (context.modifiersInWrongOrder) {
                KotlinBundle.message("non.canonical.modifiers.order")
            } else if (context.modifiersNotAtTheEnd) {
                KotlinBundle.message("modifiers.should.be.at.the.end")
            } else {
                KotlinBundle.message("context.parameters.should.follow.annotations")
            }

        return createProblemDescriptor(
            element,
            rangeInElement,
            message,
            ProblemHighlightType.WARNING,
            onTheFly,
            SortModifiersQuickFix(context)
        )
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = modifierListVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtModifierList): List<TextRange> {
        val relevantChildren = element.orderRelevantChildren()
        if (relevantChildren.isEmpty()) return emptyList()

        val modifierTokens = element.modifierKeywordTokens()
        val modifiersInWrongOrder = modifierTokens.isNotEmpty() && modifierTokens != sortModifiers(modifierTokens)

        return if (modifiersInWrongOrder) {
            val modifierElements = relevantChildren.filter { it.node.elementType is KtModifierKeywordToken }
            val startElement = modifierElements.firstOrNull() ?: relevantChildren.first()
            val endElement = modifierElements.lastOrNull() ?: relevantChildren.last()
            listOf(TextRange(startElement.startOffset, endElement.endOffset).shiftLeft(element.startOffset))
        } else if (element.modifiersNotAtTheLastPlace()) {
            val firstModifier = relevantChildren.first { it.node.elementType is KtModifierKeywordToken }
            listOf(TextRange(firstModifier.startOffset, relevantChildren.last().endOffset).shiftLeft(element.startOffset))
        } else if (element.contextParametersBeforeAnnotations()) {
            val contextParameters = relevantChildren.first { it is KtContextParameterList }
            val lastAnnotation = relevantChildren.last { it is KtAnnotationEntry || it is KtAnnotation }
            listOf(TextRange(contextParameters.startOffset, lastAnnotation.endOffset).shiftLeft(element.startOffset))
        } else emptyList()
    }
}

private class SortModifiersQuickFix(val context: SortModifiersInspection.Context) : KotlinModCommandQuickFix<KtModifierList>() {
    override fun getFamilyName(): String = KotlinBundle.message("sort.modifiers")

    override fun applyFix(project: Project, element: KtModifierList, updater: ModPsiUpdater) {
        val owner = element.parent as? KtModifierListOwner ?: return

        if (context.contextParametersBeforeAnnotations) {
            val modifierList = element.orderRelevantChildren()
            val ordered = element.orderRelevantChildren()
                .partition { it is KtAnnotationEntry || it is KtAnnotation }
                .let { it.first + it.second }

            val firstKeywordToken = modifierList.firstOrNull { it.node.elementType is KtModifierKeywordToken }
            if (firstKeywordToken == null) {
                ordered.forEach { element.add(it) }
            } else {
                ordered.forEach { element.addBefore(it, firstKeywordToken) }
            }
            modifierList.forEach { it.delete() }
        }

        if (context.modifiersInWrongOrder || context.modifiersNotAtTheEnd) {
            val sortedModifiers = sortModifiers(element.modifierKeywordTokens())
            sortedModifiers.forEach { owner.removeModifier(it) }
            sortedModifiers
                .partition { it in KtTokens.VISIBILITY_MODIFIERS || it in KtTokens.MODALITY_MODIFIERS }
                .let { it.second + it.first }
                .forEach { owner.addModifier(it) }
        }

        owner.modifierList?.addLineBreakBeforeFirstModifierIfMissing(project)
    }
}

private fun KtModifierList.modifierKeywordTokens(): List<KtModifierKeywordToken> {
    return allChildren.mapNotNull { it.node.elementType as? KtModifierKeywordToken }.toList()
}

private fun KtModifierList.modifiersNotAtTheLastPlace(): Boolean {
    val firstModifier = allChildren.firstOrNull { it.node.elementType is KtModifierKeywordToken } ?: return false
    return firstModifier.siblings(forward = true, withItself = false)
        .filter { it is KtContextParameterList || it is KtAnnotationEntry || it is KtAnnotation }
        .toList()
        .isNotEmpty()
}

private fun KtModifierList.orderRelevantChildren(): List<PsiElement> {
    return allChildren.filter {
        it is KtAnnotationEntry ||
                it is KtAnnotation ||
                it is KtContextParameterList ||
                it.node.elementType is KtModifierKeywordToken
    }.toList()
}

private fun KtModifierList.contextParametersBeforeAnnotations(): Boolean {
    val contextParameter = allChildren.firstOrNull { it is KtContextParameterList } ?: return false
    val lastAnnotation = allChildren.lastOrNull { it is KtAnnotationEntry || it is KtAnnotation } ?: return false
    return contextParameter.before(lastAnnotation)
}

private fun KtModifierList.addLineBreakBeforeFirstModifierIfMissing(project: Project) {
    val firstModifier = allChildren.firstOrNull { it.node.elementType is KtModifierKeywordToken } ?: return
    val previousSibling = firstModifier.getPrevSiblingIgnoringWhitespace() ?: return
    val firstWhitespace = previousSibling.nextSibling as? PsiWhiteSpace
    val lastWhitespace = firstModifier.prevSibling as? PsiWhiteSpace
    if (lastWhitespace != null && firstWhitespace?.before(lastWhitespace) == true) {
        deleteChildRange(firstWhitespace, lastWhitespace)
    }
    addBefore(KtPsiFactory(project).createNewLine(), firstModifier)
}
