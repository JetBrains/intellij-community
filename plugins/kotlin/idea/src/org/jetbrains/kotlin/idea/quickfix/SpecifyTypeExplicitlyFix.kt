// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.types.isError

class SpecifyTypeExplicitlyFix : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = KotlinBundle.message("specify.type.explicitly")

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val declaration = declarationByElement(element)!!
        val type = SpecifyTypeExplicitlyIntention.getTypeForDeclaration(declaration)
        SpecifyTypeExplicitlyIntention.addTypeAnnotation(editor, declaration, type)
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val declaration = declarationByElement(element)
        if (declaration?.typeReference != null) return false
        text = when (declaration) {
            is KtProperty -> KotlinBundle.message("specify.type.explicitly")
            is KtNamedFunction -> KotlinBundle.message("specify.return.type.explicitly")
            else -> return false
        }

        return !SpecifyTypeExplicitlyIntention.getTypeForDeclaration(declaration).isError
    }

    private fun declarationByElement(element: PsiElement): KtCallableDeclaration? {
        return PsiTreeUtil.getParentOfType(element, KtProperty::class.java, KtNamedFunction::class.java) as KtCallableDeclaration?
    }
}
