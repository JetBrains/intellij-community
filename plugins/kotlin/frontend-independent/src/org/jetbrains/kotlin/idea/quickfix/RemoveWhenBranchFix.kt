// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class RemoveWhenBranchFix(
    element: KtWhenEntry,
    private val elseBranch: Boolean,
) : PsiUpdateModCommandAction<KtWhenEntry>(element) {
    override fun getFamilyName(): @IntentionFamilyName String =
        if (elseBranch) KotlinBundle.message("remove.else.branch") else KotlinBundle.message("remove.branch")

    override fun invoke(
        context: ActionContext,
        element: KtWhenEntry,
        updater: ModPsiUpdater,
    ): Unit = element.delete()

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val whenEntry = psiElement.getParentOfType<KtWhenEntry>(strict = false)
            return if (whenEntry != null && (whenEntry.isElse || whenEntry.conditions.size == 1)) {
                listOf(RemoveWhenBranchFix(whenEntry, whenEntry.isElse).asIntention())
            } else {
                emptyList()
            }
        }
    }
}