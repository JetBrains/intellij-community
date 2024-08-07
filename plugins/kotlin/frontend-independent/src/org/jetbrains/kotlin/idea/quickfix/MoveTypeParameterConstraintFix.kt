// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class MoveTypeParameterConstraintFix(element: KtTypeParameter) : PsiUpdateModCommandAction<KtTypeParameter>(element), CleanupFix.ModCommand {

    override fun getFamilyName(): String = KotlinBundle.message("move.type.parameter.constraint.to.where.clause")

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeParameter,
        updater: ModPsiUpdater,
    ) {
        val typeParameterName = element.nameAsName ?: return
        val psiFactory = KtPsiFactory(actionContext.project)
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
}
