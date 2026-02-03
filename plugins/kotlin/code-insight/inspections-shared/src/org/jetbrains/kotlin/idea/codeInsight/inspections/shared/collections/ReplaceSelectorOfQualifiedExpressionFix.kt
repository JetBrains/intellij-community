// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ReplaceSelectorOfQualifiedExpressionFix(private val newSelector: String) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = KotlinBundle.message("replace.with.0", newSelector)

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        (element as? KtQualifiedExpression)?.let {
            it.replace(KtPsiFactory(project).createExpressionByPattern("$0.$newSelector", it.receiverExpression))
        }
    }
}
