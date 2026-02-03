// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPsiFactory

class MissingConstructorKeywordFix(element: KtPrimaryConstructor) : PsiUpdateModCommandAction<KtPrimaryConstructor>(element), CleanupFix.ModCommand {
    override fun getFamilyName(): String = KotlinBundle.message("add.constructor.keyword")

    override fun invoke(
        actionContext: ActionContext,
        element: KtPrimaryConstructor,
        updater: ModPsiUpdater
    ) {
        element.addConstructorKeyword()
    }

    private fun KtPrimaryConstructor.addConstructorKeyword(): PsiElement? {
        val modifierList = this.modifierList ?: return null
        val psiFactory = KtPsiFactory(project)
        val constructor = if (valueParameterList == null) {
            psiFactory.createPrimaryConstructor("constructor()")
        } else {
            psiFactory.createConstructorKeyword()
        }
        return addAfter(constructor, modifierList)
    }
}