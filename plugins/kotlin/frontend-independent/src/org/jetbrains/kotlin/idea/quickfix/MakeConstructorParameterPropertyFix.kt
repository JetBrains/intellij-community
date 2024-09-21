// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.nonStaticOuterClasses
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

class MakeConstructorParameterPropertyFix(
    element: KtParameter,
    private val kotlinValVar: KotlinValVar,
    private val className: String?,
) : PsiUpdateModCommandAction<KtParameter>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("make.constructor.parameter.a.property.0", "")

    override fun getPresentation(
        context: ActionContext,
        element: KtParameter,
    ): Presentation {
        val suffix = if (className != null) KotlinBundle.message("in.class.0", className) else ""
        return Presentation.of(KotlinBundle.message("make.constructor.parameter.a.property.0", suffix))
    }

    override fun invoke(
        context: ActionContext,
        element: KtParameter,
        updater: ModPsiUpdater,
    ) {
        element.addBefore(kotlinValVar.createKeyword(KtPsiFactory(context.project))!!, element.nameIdentifier)
        element.addModifier(KtTokens.PRIVATE_KEYWORD)
        element.visibilityModifier()?.let { private ->
            updater.select(private)
            updater.moveCaretTo(private.endOffset)
        }
    }
}

fun KtNameReferenceExpression.getPrimaryConstructorParameterWithSameName(): KtParameter? {
    return nonStaticOuterClasses()
        .mapNotNull { ktClass -> ktClass.primaryConstructor?.valueParameters?.firstOrNull { it.name == getReferencedName() } }
        .firstOrNull()
}
