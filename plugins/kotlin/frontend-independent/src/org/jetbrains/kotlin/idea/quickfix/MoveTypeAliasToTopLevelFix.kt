// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeAlias

class MoveTypeAliasToTopLevelFix(element: KtTypeAlias) : PsiUpdateModCommandAction<KtTypeAlias>(element) {

    override fun getFamilyName() = KotlinBundle.message("fix.move.typealias.to.top.level")

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeAlias,
        updater: ModPsiUpdater,
    ) {
        val containingFile = element.containingKtFile
        val target = containingFile.importList ?: containingFile.packageDirective
        containingFile.addAfter(element, target)
        containingFile.addAfter(KtPsiFactory(actionContext.project).createNewLine(2), target)
        element.delete()
    }
}
