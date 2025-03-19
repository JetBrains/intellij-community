// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtQualifiedExpression

class RemoveUselessCallFix : PsiUpdateModCommandQuickFix() {

    override fun getName() = KotlinBundle.message("remove.redundant.call.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val qualifiedDescriptor = (element as? KtQualifiedExpression) ?: return
        qualifiedDescriptor.replaced(qualifiedDescriptor.receiverExpression)
    }
}