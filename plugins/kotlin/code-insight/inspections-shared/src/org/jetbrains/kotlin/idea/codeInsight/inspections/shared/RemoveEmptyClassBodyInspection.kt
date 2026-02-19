// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.nextLeafs
import com.intellij.psi.util.prevLeafs
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class RemoveEmptyClassBodyInspection :
    KotlinApplicableInspectionBase.Simple<KtClassBody, Unit>(),
    CleanupLocalInspectionTool {

    override fun KaSession.prepareContext(element: KtClassBody): Unit = Unit

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitClassBody(element: KtClassBody) {
            super.visitClassBody(element)
            visitTargetElement(element, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(element: KtClassBody, context: Unit): String =
        KotlinBundle.message("redundant.empty.class.body")

    override fun createQuickFix(element: KtClassBody, context: Unit): KotlinModCommandQuickFix<KtClassBody> =
        object : KotlinModCommandQuickFix<KtClassBody>() {
            override fun getFamilyName(): String = KotlinBundle.message("remove.redundant.empty.class.body")

            override fun applyFix(project: Project, element: KtClassBody, updater: ModPsiUpdater) {
                val parent = element.parent
                element.delete()
                addSemicolonIfNeeded(parent, updater)
            }
        }

    private fun addSemicolonIfNeeded(element: PsiElement, updater: ModPsiUpdater?) {
        val next = element.getNextSiblingIgnoringWhitespaceAndComments() ?: return
        if (next.node.elementType == KtTokens.SEMICOLON) return
        when (element) {
            is KtObjectDeclaration -> {
                if (!element.isCompanion() || element.nameIdentifier != null) return
                val firstChildNode = next.firstChild?.node ?: return
                if (firstChildNode.elementType in KtTokens.KEYWORDS) return
            }

            is KtEnumEntry -> {
                val children = element.parent.children
                val isLastChild = element == children.lastOrNull()
                val isLastEnumEntry = element == children.lastOrNull { it is KtEnumEntry }
                if (isLastChild || !isLastEnumEntry) return
            }

            is KtClass -> {
                if (!element.isLocal) return
                if (element.nextLeafs.firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }?.elementType != KtTokens.LPAR) return
                if (next.prevLeafs.firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }?.elementType == KtTokens.RPAR) return
            }

            else -> return
        }

        val semicolon = element.addSemicolon()
        updater?.moveCaretTo(semicolon.endOffset)
    }

    private fun PsiElement.addSemicolon(): PsiElement {
        val semicolon = KtPsiFactory(project).createSemicolon()
        return if (this is KtEnumEntry) {
            add(semicolon)
        } else {
            parent.addAfter(semicolon, this)
        }
    }

    override fun isApplicableByPsi(element: KtClassBody): Boolean {
        element.getStrictParentOfType<KtObjectDeclaration>()?.let {
            if (it.isObjectLiteral()) return false
        }

        element.getStrictParentOfType<KtClass>()?.let {
            if (!it.isTopLevel() && it.getNextSiblingIgnoringWhitespaceAndComments() is KtSecondaryConstructor) return false
        }

        return element.text.replace("{", "").replace("}", "").isBlank()
    }
}