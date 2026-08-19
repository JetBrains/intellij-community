// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.childrenOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.addModifierKeyword
import org.jetbrains.kotlin.idea.base.psi.removeModifierKeyword
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.api.KDocElement
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
class SortModifiersInspection : KotlinApplicableInspectionBase<KtModifierListOwner, SortModifiersInspection.Context>(),
    CleanupLocalInspectionTool {

    data class Context(
        val modifiersInWrongOrder: Boolean = false, //visibility/modality modifiers
        val modifiersNotAtTheEnd: Boolean = false, // they precede annotations or context parameters
        val contextParametersBeforeAnnotations: Boolean = false,
        val kDocIsMisplaced: Boolean = false // should precede annotations/modifiers/context parameters
    )

    context(session: KaSession)
    override fun prepareContext(element: KtModifierListOwner): Context = Context(
        modifiersInWrongOrder = element.modifiersInWrongOrder(),
        modifiersNotAtTheEnd = element.modifiersNotAtTheLastPlace(),
        contextParametersBeforeAnnotations = element.contextParametersBeforeAnnotations(),
        kDocIsMisplaced = element.misplacedKDoc() != null
    )

    override fun isApplicableByPsi(element: KtModifierListOwner): Boolean =
        element.misplacedKDoc() != null ||
                element.modifiersInWrongOrder() ||
                element.modifiersNotAtTheLastPlace() ||
                element.contextParametersBeforeAnnotations()

    override fun InspectionManager.createProblemDescriptor(
        element: KtModifierListOwner,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val message =
            if (context.kDocIsMisplaced) {
                KotlinBundle.message("kdoc.should.precede.modifiers")
            } else if (context.modifiersInWrongOrder) {
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
        val owner = it.parent as? KtModifierListOwner ?: return@modifierListVisitor
        visitTargetElement(owner, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtModifierListOwner): List<TextRange> {
        val relevantElements = element.orderRelevantElements()
        if (relevantElements.isEmpty()) return emptyList()

        val (startElement, endElement) = if (element.misplacedKDoc() != null) {
            relevantElements.first() to relevantElements.last()
        } else if (element.modifiersInWrongOrder()) {
            val modifiers = relevantElements.filter { it.isModifierKeyword() }
            modifiers.first() to modifiers.last()
        } else if (element.modifiersNotAtTheLastPlace()) {
            relevantElements.first { it.isModifierKeyword() } to relevantElements.last()
        } else if (element.contextParametersBeforeAnnotations()) {
            relevantElements.first { it is KtContextParameterList } to
                    relevantElements.last { it.isAnnotation() }
        } else return emptyList()

        return listOf(TextRange(startElement.startOffset, endElement.endOffset).shiftLeft(element.startOffset))
    }
}

private class SortModifiersQuickFix(val context: SortModifiersInspection.Context) : KotlinModCommandQuickFix<KtModifierListOwner>() {
    override fun getFamilyName(): String = KotlinBundle.message("sort.modifiers")

    override fun applyFix(project: Project, element: KtModifierListOwner, updater: ModPsiUpdater) {
        if (context.kDocIsMisplaced) {
            element.moveKDocBeforeDeclaration()
        }
        if (context.contextParametersBeforeAnnotations) {
            element.moveContextParametersAfterAnnotations()
        }
        if (context.modifiersInWrongOrder || context.modifiersNotAtTheEnd) {
            element.moveModifiersToRightPlace()
        }
        element.modifierList?.addLineBreakBeforeFirstModifierIfMissing(project)
    }
}

private fun PsiElement.isModifierKeyword(): Boolean = node.elementType is KtModifierKeywordToken

private fun PsiElement.isAnnotation(): Boolean = this is KtAnnotationEntry || this is KtAnnotation

private fun KtModifierListOwner.modifierKeywordTokens(): List<KtModifierKeywordToken> {
    return modifierList?.allChildren?.mapNotNull { it.node.elementType as? KtModifierKeywordToken }?.toList() ?: emptyList()
}

private fun KtModifierListOwner.modifiersInWrongOrder(): Boolean {
    val modifierTokens = modifierKeywordTokens()
    return modifierTokens.isNotEmpty() && modifierTokens != sortModifiers(modifierTokens)
}

private fun KtModifierListOwner.modifiersNotAtTheLastPlace(): Boolean {
    val ktModifierList = childrenOfType<KtModifierList>().first()
    val firstModifier = ktModifierList.allChildren.firstOrNull { it.isModifierKeyword() } ?: return false
    return firstModifier.siblings(forward = true, withItself = false)
        .filter { it is KtContextParameterList || it.isAnnotation() }
        .toList()
        .isNotEmpty()
}

// KDoc can be misplaced and become a part of KtModifierList. Or it can be put before the function declaration, outside the KtModifierList.
// In the latter case, it is a part of KtModifierListOwner, is a sibling of KtModifierList
private fun KtModifierListOwner.misplacedKDoc(): KDoc? {
    val (kDocs, others) = orderRelevantElements().partition { it is KDocElement }
    val kDoc = kDocs.lastOrNull() as? KDoc ?: return null
    return kDoc.takeIf { others.firstOrNull()?.before(kDoc) == true }
}

private fun PsiElement.isOrderRelevant(): Boolean =
    this.isAnnotation() || this is KtContextParameterList || this is KDocElement || this.isModifierKeyword()

private fun KtModifierListOwner.orderRelevantElements(): List<PsiElement> =
    allChildren.flatMap {
        when (it) {
            is KtModifierList -> it.allChildren.filter { child -> child.isOrderRelevant() }
            is KDocElement -> sequenceOf(it)
            else -> emptySequence()
        }
    }.toList()

private fun KtModifierListOwner.contextParametersBeforeAnnotations(): Boolean {
    val relevantElements = orderRelevantElements()

    val contextParameter = relevantElements.firstOrNull { it is KtContextParameterList } ?: return false
    val lastAnnotation = relevantElements.lastOrNull { it.isAnnotation() } ?: return false
    return contextParameter.before(lastAnnotation)
}

private fun KtModifierListOwner.moveKDocBeforeDeclaration() {
    val (kDocs, others) = orderRelevantElements().partition { it is KDocElement }
    val kDoc = kDocs.lastOrNull() as? KDoc ?: return
    val anchor = others.firstOrNull()?.parent ?: return
    addBefore(kDoc, anchor)
    kDoc.delete()
}

private fun KtModifierListOwner.moveContextParametersAfterAnnotations() {
    val modifierList = modifierList ?: return
    val relevantChildren = modifierList.allChildren.filter { it.isOrderRelevant() }.toList()
    val ordered = relevantChildren.partition { it.isAnnotation() }.let { it.first + it.second }

    val firstKeywordToken = relevantChildren.firstOrNull { it.isModifierKeyword() }
    if (firstKeywordToken == null) {
        ordered.forEach { modifierList.add(it) }
    } else {
        ordered.forEach { modifierList.addBefore(it, firstKeywordToken) }
    }
    relevantChildren.forEach { it.delete() }
}

private fun KtModifierListOwner.moveModifiersToRightPlace() {
    val sortedModifiers = sortModifiers(modifierKeywordTokens())
    sortedModifiers.forEach { removeModifierKeyword(it) }
    sortedModifiers
        .partition { it in KtTokens.VISIBILITY_MODIFIERS || it in KtTokens.MODALITY_MODIFIERS }
        .let { it.second + it.first }
        .forEach { addModifierKeyword(it) }
}

private fun KtModifierList.addLineBreakBeforeFirstModifierIfMissing(project: Project) {
    val firstModifier = allChildren.firstOrNull { it.isModifierKeyword() } ?: return
    val previousSibling = firstModifier.getPrevSiblingIgnoringWhitespace() ?: return
    val firstWhitespace = previousSibling.nextSibling as? PsiWhiteSpace
    val lastWhitespace = firstModifier.prevSibling as? PsiWhiteSpace
    if (lastWhitespace != null && firstWhitespace?.before(lastWhitespace) == true) {
        deleteChildRange(firstWhitespace, lastWhitespace)
    }
    addBefore(KtPsiFactory(project).createNewLine(), firstModifier)
}
