// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

object RemoveRedundantSemicolonFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = KotlinBundle.message("redundant.semicolon.text")

    override fun applyFix(
        project: Project,
        element: PsiElement,
        updater: ModPsiUpdater
    ) {
        element.delete()
    }
}