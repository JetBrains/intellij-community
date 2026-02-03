// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Handles "Go to Declaration" for destructuring declaration entries.
 *
 * For both short-form and full-form,
 * Can be removed when this one is fixed (KT-82708): Only the initializer symbol is expected.
 */
class KotlinDestructuringGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (sourceElement == null) return null
        val entry = findDestructuringEntry(sourceElement) ?: return null
        val reference = entry.reference as? PsiPolyVariantReference ?: return null
        val results = reference.multiResolve(false)

        // Can be changed when this one is fixed (KT-82708): Only the initializer symbol is expected
        val targets = results
            .mapNotNull { it.element }
            .filter { it !is KtDestructuringDeclarationEntry }

        return if (targets.isNotEmpty()) targets.toTypedArray() else null
    }

    private fun findDestructuringEntry(sourceElement: PsiElement): KtDestructuringDeclarationEntry? {
        val parent = sourceElement.parent

        if (parent is KtNameReferenceExpression) {
            val entry = parent.parent as? KtDestructuringDeclarationEntry ?: return null
            val destructuring = entry.parent as? KtDestructuringDeclaration ?: return null
            if (destructuring.isFullForm) {
                return entry
            }
        }
        return null
    }
}