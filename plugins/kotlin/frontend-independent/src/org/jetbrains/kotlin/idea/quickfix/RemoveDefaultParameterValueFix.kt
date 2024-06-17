// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtParameter

class RemoveDefaultParameterValueFix(
    element: KtParameter,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtParameter, Unit>(element, Unit) {

    override fun getFamilyName() = KotlinBundle.message("remove.default.parameter.value")

    override fun invoke(
        actionContext: ActionContext,
        element: KtParameter,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val typeReference = element.typeReference ?: return
        val defaultValue = element.defaultValue ?: return
        val commentSaver = CommentSaver(element)
        element.deleteChildRange(typeReference.nextSibling, defaultValue)
        commentSaver.restore(element)
    }
}
