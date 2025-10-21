// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class ChangeContextFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String {
        return KotlinBundle.message("intention.switch.context.to.dispatchers.io")
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val callExpression = element
            .parentsOfType<KtCallExpression>()
            .firstOrNull { it.calleeExpression?.textMatches("withContext") ?: false }
            ?: return

        val ktPsiFactory = KtPsiFactory(project, true)
        val replacedArgument = analyze(callExpression) {
            callExpression.resolveToCall()?.successfulCallOrNull<KaCall>()
                ?.getFirstArgumentExpression()
                ?.replaced(ktPsiFactory.createExpression("kotlinx.coroutines.Dispatchers.IO")) ?: return
        }

        CoroutineBlockingCallInspectionUtils.postProcessQuickFix(replacedArgument, project)
    }
}