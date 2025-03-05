// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddFunctionBodyFix(element: KtFunction) : PsiUpdateModCommandAction<KtFunction>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.add.function.body")

    override fun getPresentation(context: ActionContext, element: KtFunction): Presentation? =
        Presentation.of(familyName).takeIf { !element.hasBody() }

    override fun invoke(context: ActionContext, element: KtFunction, updater: ModPsiUpdater) {
        if (!element.hasBody()) {
            element.add(KtPsiFactory(context.project).createEmptyBody())
        }
    }

    companion object : QuickFixesPsiBasedFactory<KtFunction>(KtFunction::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtFunction): List<IntentionAction> =
            listOfNotNull(
                AddFunctionBodyFix(psiElement).asIntention()
            )
    }
}
