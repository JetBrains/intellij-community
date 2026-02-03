// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.IO_DISPATCHER_FQN
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.WITH_CONTEXT_FQN
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class WrapInWithContextFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String {
        return KotlinBundle.message("intention.wrap.in.with.context")
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val callExpression = element.parentOfType<KtCallExpression>() ?: return
        val effectiveExpressionToWrap: KtElement = callExpression.parentOfType<KtDotQualifiedExpression>() ?: callExpression
        val ktPsiFactory = KtPsiFactory(project)
        val wrappedBlockingCall = effectiveExpressionToWrap.replaced(
            ktPsiFactory.createExpression(
                """
                    |$WITH_CONTEXT_FQN($IO_DISPATCHER_FQN) {
                    |   ${effectiveExpressionToWrap.text}
                    |}
                """.trimMargin()
            )
        )
        CoroutineBlockingCallInspectionUtils.postProcessQuickFix(wrappedBlockingCall, project)
    }
}