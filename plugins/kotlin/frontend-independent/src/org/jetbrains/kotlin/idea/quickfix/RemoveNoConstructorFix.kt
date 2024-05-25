// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveNoConstructorFix(
    element: KtValueArgumentList,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtValueArgumentList, Unit>(element, Unit) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtValueArgumentList,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val superTypeCallEntry = element.getStrictParentOfType<KtSuperTypeCallEntry>() ?: return
        val superTypeEntry = KtPsiFactory(actionContext.project).createSuperTypeEntry(superTypeCallEntry.firstChild.text)
        superTypeCallEntry.replaced(superTypeEntry)
    }

    override fun getFamilyName() = KotlinBundle.message("remove.constructor.call")
}
