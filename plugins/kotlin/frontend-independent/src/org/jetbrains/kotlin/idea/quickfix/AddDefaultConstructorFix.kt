// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.createPrimaryConstructorIfAbsent

class AddDefaultConstructorFix(element: KtClass) : PsiUpdateModCommandAction<KtClass>(element) {

    override fun getFamilyName() = KotlinBundle.message("fix.add.default.constructor")

    override fun invoke(
        actionContext: ActionContext,
        element: KtClass,
        updater: ModPsiUpdater,
    ) {
        element.createPrimaryConstructorIfAbsent()
    }
}
