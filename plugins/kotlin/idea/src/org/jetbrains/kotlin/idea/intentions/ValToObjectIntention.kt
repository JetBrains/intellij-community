// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.FileModificationService
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class ValToObjectIntention : SelfTargetingIntention<KtProperty>(
    KtProperty::class.java,
    KotlinBundle.lazyMessage("convert.to.object.declaration")
) {

    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtProperty, caretOffset: Int): Boolean {
        if (element.isVar) return false
        if (!element.isTopLevel) return false

        val initializer = element.initializer as? KtObjectLiteralExpression ?: return false
        if (initializer.objectDeclaration.body == null) return false

        if (element.getter != null) return false
        if (element.annotationEntries.isNotEmpty()) return false

        // disable if has non-Kotlin usages
        return ReferencesSearch.search(element).all { it is KtReference && it.element.parent !is KtCallableReferenceExpression }
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return

        val name = element.name ?: return
        val objectLiteral = element.initializer as? KtObjectLiteralExpression ?: return
        val declaration = objectLiteral.objectDeclaration
        val superTypeList = declaration.getSuperTypeList()
        val body = declaration.body ?: return

        val prefix = element.modifierList?.text?.plus(" ") ?: ""
        val superTypesText = superTypeList?.text?.plus(" ") ?: ""

        val replacementText = "${prefix}object $name: $superTypesText${body.text}"
        val replaced = runWriteAction {
            val psiFactory = KtPsiFactory(element.project)
            element.replaced(psiFactory.createDeclarationByPattern<KtObjectDeclaration>(replacementText))
        }

        editor?.caretModel?.moveToOffset(replaced.nameIdentifier?.endOffset ?: return)
    }
}