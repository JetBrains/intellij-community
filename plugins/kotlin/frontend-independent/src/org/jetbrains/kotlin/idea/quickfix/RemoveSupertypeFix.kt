// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveSupertypeFix(element: KtSuperTypeListEntry) : PsiUpdateModCommandAction<KtSuperTypeListEntry>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.supertype")

    override fun invoke(
        actionContext: ActionContext,
        element: KtSuperTypeListEntry,
        updater: ModPsiUpdater,
    ) {
        element.getStrictParentOfType<KtClassOrObject>()?.removeSuperTypeListEntry(element)
    }
}
