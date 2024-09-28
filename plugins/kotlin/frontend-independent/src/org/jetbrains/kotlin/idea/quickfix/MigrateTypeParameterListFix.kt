// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class MigrateTypeParameterListFix(
    element: KtTypeParameterList,
) : PsiUpdateModCommandAction<KtTypeParameterList>(element), CleanupFix.ModCommand {

    override fun getFamilyName(): String = KotlinBundle.message("migrate.type.parameter.list.syntax")

    override fun invoke(
        context: ActionContext,
        element: KtTypeParameterList,
        updater: ModPsiUpdater,
    ) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        function.addBefore(element, function.receiverTypeReference ?: function.nameIdentifier)
        element.delete()
    }
}
