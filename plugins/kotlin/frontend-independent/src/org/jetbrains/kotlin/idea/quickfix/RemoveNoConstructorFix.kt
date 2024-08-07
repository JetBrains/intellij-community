// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry

class RemoveNoConstructorFix(element: KtSuperTypeCallEntry) : PsiUpdateModCommandAction<KtSuperTypeCallEntry>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtSuperTypeCallEntry,
        updater: ModPsiUpdater,
    ) {
        val superTypeEntry = KtPsiFactory(actionContext.project).createSuperTypeEntry(element.firstChild.text)
        element.replaced(superTypeEntry)
    }

    override fun getFamilyName() = KotlinBundle.message("remove.constructor.call")
}
