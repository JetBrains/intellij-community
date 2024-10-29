// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveFinalUpperBoundFix(element: KtTypeReference) : PsiUpdateModCommandAction<KtTypeReference>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeReference,
        updater: ModPsiUpdater,
    ) {
        element.getStrictParentOfType<KtTypeParameter>()?.extendsBound = null
    }

    override fun getFamilyName(): String = KotlinBundle.message("remove.final.upper.bound")
}
