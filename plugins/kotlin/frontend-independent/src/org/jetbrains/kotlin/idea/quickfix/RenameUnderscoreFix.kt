// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtDeclaration

class RenameUnderscoreFix(element: KtDeclaration) : KotlinPsiUpdateModCommandAction.ElementBased<KtDeclaration, Unit>(element, Unit) {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("rename.identifier.fix.text")
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtDeclaration,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        if (element !is PsiNameIdentifierOwner) return
        updater.rename(element, emptyList<String>())
    }
}