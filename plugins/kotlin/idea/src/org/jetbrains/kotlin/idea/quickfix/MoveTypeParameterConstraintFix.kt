// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class MoveTypeParameterConstraintFix(element: KtTypeParameter) : KotlinQuickFixAction<KtTypeParameter>(element), CleanupFix {
    override fun getText(): String = KotlinBundle.message("move.type.parameter.constraint.to.where.clause")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val typeParameterName = element.nameAsName ?: return
        val psiFactory = KtPsiFactory(project)
        val templateClass = psiFactory.buildDeclaration {
            appendFixedText("class A<")
            appendName(typeParameterName)
            appendFixedText("> where ")
            appendName(typeParameterName)
            appendFixedText(":")
            appendTypeReference(element.extendsBound)
        } as KtTypeParameterListOwner
        val templateConstraintList = templateClass.typeConstraintList!!

        val declaration = element.getStrictParentOfType<KtTypeParameterListOwner>() ?: return
        val constraintList = declaration.typeConstraintList ?: return
        constraintList.addAfter(psiFactory.createComma(), null)
        constraintList.addAfter(templateConstraintList.constraints[0], null)

        element.extendsBound?.delete()
        val colon = element.node.findChildByType(KtTokens.COLON)
        colon?.psi?.delete()
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val typeParameter = diagnostic.psiElement as? KtTypeParameter ?: return null
            return MoveTypeParameterConstraintFix(typeParameter)
        }
    }
}
