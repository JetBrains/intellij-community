// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.addRemoveModifier.sortModifiers
import org.jetbrains.kotlin.psi.modifierListVisitor
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@ApiStatus.Internal
@IntellijInternalApi
class SortModifiersInspection : KotlinApplicableInspectionBase.Simple<KtModifierList, Unit>(), CleanupLocalInspectionTool {

    override fun KaSession.prepareContext(element: KtModifierList) {}

    override fun isApplicableByPsi(element: KtModifierList): Boolean {
        val modifiers = element.modifierKeywordTokens()
        if (modifiers.isEmpty()) return false
        val sortedModifiers = sortModifiers(modifiers)
        return modifiers != sortedModifiers || element.modifiersBeforeAnnotations()
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = modifierListVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtModifierList): List<TextRange> {
        val modifierElements = element.allChildren.toList()
        val startElement = modifierElements.firstOrNull { it.node.elementType is KtModifierKeywordToken } ?: return emptyList()
        val endElement = modifierElements.lastOrNull { it.node.elementType is KtModifierKeywordToken } ?: return emptyList()
        return listOf(TextRange(startElement.startOffset, endElement.endOffset).shiftLeft(element.startOffset))
    }

    override fun getProblemDescription(element: KtModifierList, context: Unit): String =
        if (element.modifiersBeforeAnnotations()) KotlinBundle.message("modifiers.should.follow.annotations") else KotlinBundle.message("non.canonical.modifiers.order")

    override fun createQuickFix(
        element: KtModifierList,
        context: Unit,
    ): KotlinModCommandQuickFix<KtModifierList> = object : KotlinModCommandQuickFix<KtModifierList>() {
        override fun getFamilyName(): String = KotlinBundle.message("sort.modifiers")

        override fun applyFix(project: Project, element: KtModifierList, updater: ModPsiUpdater) {
            val owner = element.parent as? KtModifierListOwner ?: return
            val sortedModifiers = sortModifiers(element.modifierKeywordTokens())
            val existingModifiers = sortedModifiers.filter { owner.hasModifier(it) }
            existingModifiers.forEach { owner.removeModifier(it) }
            // We add visibility / modality modifiers after all others,
            // because they can be redundant or not depending on others (e.g. override)
            existingModifiers
                .partition { it in KtTokens.VISIBILITY_MODIFIERS || it in KtTokens.MODALITY_MODIFIERS }
                .let { it.second + it.first }
                .forEach { owner.addModifier(it) }
        }
    }

    private fun KtModifierList.modifierKeywordTokens(): List<KtModifierKeywordToken> {
        return allChildren.mapNotNull { it.node.elementType as? KtModifierKeywordToken }.toList()
    }

    private fun KtModifierList.modifiersBeforeAnnotations(): Boolean {
        val modifierElements = this.allChildren.toList()
        var modifiersBeforeAnnotations = false
        var seenModifiers = false
        for (modifierElement in modifierElements) {
            if (modifierElement.node.elementType is KtModifierKeywordToken) {
                seenModifiers = true
            } else if (seenModifiers && (modifierElement is KtAnnotationEntry || modifierElement is KtAnnotation)) {
                modifiersBeforeAnnotations = true
            }
        }
        return modifiersBeforeAnnotations
    }
}