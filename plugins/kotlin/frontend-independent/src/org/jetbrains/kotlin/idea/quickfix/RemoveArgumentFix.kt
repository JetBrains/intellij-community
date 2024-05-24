// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

class RemoveArgumentFix(
    element: KtValueArgument,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtValueArgument, Unit>(element, Unit) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.remove.argument.text")

    override fun invoke(
        actionContext: ActionContext,
        element: KtValueArgument,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        if (element is KtLambdaArgument) {
            element.delete()
        } else {
            (element.parent as? KtValueArgumentList)?.removeArgument(element)
        }
    }
}
