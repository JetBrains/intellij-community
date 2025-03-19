// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtWhenEntryGuard

class ReplaceAndWithWhenGuardFix(
    element: PsiErrorElement,
    private val newGuard: KtWhenEntryGuard
) : PsiUpdateModCommandAction<PsiErrorElement>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("replace.and.with.when.guard")

    override fun invoke(
        context: ActionContext,
        element: PsiErrorElement,
        updater: ModPsiUpdater
    ) {
        element.replace(newGuard)
    }
}