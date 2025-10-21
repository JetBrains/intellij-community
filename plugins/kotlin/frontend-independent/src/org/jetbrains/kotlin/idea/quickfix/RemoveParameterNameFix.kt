// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtParameter

class RemoveParameterNameFix(element: KtParameter) : PsiUpdateModCommandAction<KtParameter>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.parameter.name")

    override fun invoke(context: ActionContext, element: KtParameter, updater: ModPsiUpdater) {
        element.nameIdentifier?.delete()
        element.colon?.delete()
    }

}
