// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass

class MakeClassAnAnnotationClassFix(element: KtClass) : PsiUpdateModCommandAction<KtClass>(element) {

    override fun getFamilyName(): String =
        KotlinBundle.message("make.class.an.annotation.class")

    override fun getPresentation(
        context: ActionContext,
        element: KtClass,
    ): Presentation = Presentation.of(
        KotlinBundle.message("make.0.an.annotation.class", element.name.toString()),
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtClass,
        updater: ModPsiUpdater,
    ) {
        element.addModifier(KtTokens.ANNOTATION_KEYWORD)
    }
}
