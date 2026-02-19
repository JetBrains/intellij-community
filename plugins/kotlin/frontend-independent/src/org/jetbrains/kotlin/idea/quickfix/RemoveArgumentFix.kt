// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

class RemoveArgumentFix(element: KtValueArgument) : PsiUpdateModCommandAction<KtValueArgument>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.remove.argument.text")

    override fun invoke(
        actionContext: ActionContext,
        element: KtValueArgument,
        updater: ModPsiUpdater,
    ) {
        if (element is KtLambdaArgument) {
            element.delete()
        } else {
            (element.parent as? KtValueArgumentList)?.removeArgument(element)
        }
    }
}
