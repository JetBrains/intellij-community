// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry

class RemoveNoConstructorFix(element: KtSuperTypeCallEntry) :
    KotlinPsiUpdateModCommandAction.ElementContextless<KtSuperTypeCallEntry>(element) {

    override fun invoke(
        context: ActionContext,
        element: KtSuperTypeCallEntry,
        updater: ModPsiUpdater,
    ) {
        val superTypeEntry = KtPsiFactory(context.project).createSuperTypeEntry(element.firstChild.text)
        element.replaced(superTypeEntry)
    }

    override fun getFamilyName(): String = KotlinBundle.message("remove.constructor.call")
}
