// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveEmptyClassBodyInspection : AbstractApplicabilityBasedInspection<KtClassBody>(KtClassBody::class.java),
                                       CleanupLocalInspectionTool {
    override fun inspectionText(element: KtClassBody): String {
        return KotlinBundle.message("redundant.empty.class.body")
    }

    override val defaultFixText: String
        get() = KotlinBundle.message("remove.redundant.empty.class.body")

    override fun applyTo(element: KtClassBody, project: Project, editor: Editor?) {
        val parent = element.parent
        element.delete()
        addSemicolonIfNeeded(parent, editor)
    }

    private fun addSemicolonIfNeeded(element: PsiElement, editor: Editor?) {
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

            else -> return
        }

        val semicolon = element.addSemicolon()
        editor?.caretModel?.moveToOffset(semicolon.endOffset)
    }

    private fun PsiElement.addSemicolon(): PsiElement {
        val semicolon = KtPsiFactory(project).createSemicolon()
        return if (this is KtEnumEntry) {
            add(semicolon)
        } else {
            parent.addAfter(semicolon, this)
        }
    }

    override fun isApplicable(element: KtClassBody): Boolean {
        element.getStrictParentOfType<KtObjectDeclaration>()?.let {
            if (it.isObjectLiteral()) return false
        }

        element.getStrictParentOfType<KtClass>()?.let {
            if (!it.isTopLevel() && it.getNextSiblingIgnoringWhitespaceAndComments() is KtSecondaryConstructor) return false
        }

        return element.text.replace("{", "").replace("}", "").isBlank()
    }
}