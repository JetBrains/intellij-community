// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class RemoveNameFromFunctionExpressionFix(
    element: KtNamedFunction,
    private val wereAutoLabelUsages: Boolean,
) : PsiUpdateModCommandAction<KtNamedFunction>(element), CleanupFix.ModCommand {

    override fun getFamilyName(): String = KotlinBundle.message("remove.identifier.from.anonymous.function")

    override fun invoke(
        context: ActionContext,
        element: KtNamedFunction,
        updater: ModPsiUpdater,
    ) {
        val name = element.nameAsName ?: return
        element.nameIdentifier?.delete()

        if (wereAutoLabelUsages) {
            val psiFactory = KtPsiFactory(element.project)
            val newFunction = psiFactory.createExpressionByPattern("$0@ $1", name, element)
            element.replace(newFunction)
        }
    }
}
