// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class ValToObjectIntention: KotlinApplicableModCommandAction<KtProperty, Unit>(KtProperty::class) {
    override fun getFamilyName(): String = KotlinBundle.message("convert.to.object.declaration")

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (element.isVar) return false
        if (!element.isTopLevel) return false

        val initializer = element.initializer as? KtObjectLiteralExpression ?: return false
        if (initializer.objectDeclaration.body == null) return false

        return element.getter == null && element.annotationEntries.isEmpty()
    }

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        // disable if has non-Kotlin usages
        return if (ReferencesSearch.search(element).asIterable().all { it is KtReference && it.element.parent !is KtCallableReferenceExpression }) {
            Unit
        } else {
            null
        }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val name = element.name ?: return
        val objectLiteral = element.initializer as? KtObjectLiteralExpression ?: return
        val declaration = objectLiteral.objectDeclaration
        val superTypeList = declaration.getSuperTypeList()
        val body = declaration.body ?: return

        val prefix = element.modifierList?.text?.plus(" ") ?: ""
        val superTypesText = superTypeList?.text?.plus(" ") ?: ""

        val replacementText = "${prefix}object $name: $superTypesText${body.text}"
        val psiFactory = KtPsiFactory(element.project)
        val replaced = element.replaced(psiFactory.createDeclarationByPattern<KtObjectDeclaration>(replacementText))

        replaced.nameIdentifier?.endOffset?.let { updater.moveCaretTo(it) }
    }
}