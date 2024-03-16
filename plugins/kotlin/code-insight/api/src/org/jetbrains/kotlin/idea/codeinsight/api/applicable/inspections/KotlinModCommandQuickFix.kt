// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement

abstract class KotlinModCommandQuickFix<ELEMENT : KtElement> : PsiUpdateModCommandQuickFix() {

    abstract override fun getName(): String

    abstract override fun getFamilyName(): @IntentionFamilyName String

    final override fun applyFix(
        project: Project,
        element: PsiElement,
        updater: ModPsiUpdater,
    ) {
        applyFix(
            project = project,
            element = @Suppress("UNCHECKED_CAST") (element as ELEMENT),
            updater = updater
        )
    }

    protected abstract fun applyFix(
        project: Project,
        element: ELEMENT,
        updater: ModPsiUpdater,
    )
}
