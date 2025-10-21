// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.getAddJvmStaticApplicabilityRange
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class MakeMemberStaticFix(
    element: KtNamedDeclaration,
) : PsiUpdateModCommandAction<KtNamedDeclaration>(element) {

    override fun getFamilyName(): String =
        KotlinBundle.message("making.member.static")

    override fun getPresentation(context: ActionContext, element: KtNamedDeclaration): Presentation? {
        val name = element.name ?: return null
        return Presentation.of(KotlinBundle.message("make.member.static.quickfix", name))
    }

    override fun invoke(
        context: ActionContext,
        element: KtNamedDeclaration,
        updater: ModPsiUpdater,
    ) {
        var declaration = element
        if (declaration is KtClass) {
            if (declaration.hasModifier(KtTokens.INNER_KEYWORD)) declaration.removeModifier(KtTokens.INNER_KEYWORD)
        } else {
            val containingClass = declaration.containingClassOrObject ?: return
            if (containingClass is KtClass) {
                val companionObject = containingClass.getOrCreateCompanionObject()

                val declarationCopy = declaration.copy() as KtNamedDeclaration
                val movedDeclaration = companionObject.addDeclarationBefore(declarationCopy, null)

                declaration.delete()
                declaration = movedDeclaration
            }
        }
        if (getAddJvmStaticApplicabilityRange(declaration) != null) {
            declaration.addAnnotation(JvmStandardClassIds.Annotations.JvmStatic)
            CodeStyleManager.getInstance(context.project).reformat(
                /* element = */ declaration,
                /* canChangeWhiteSpacesOnly = */ true
            )
        }
    }
}
