// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.findFlowOnCall
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory


internal class FlowOnIoContextFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String {
        return KotlinBundle.message("intention.flow.on.dispatchers.io")
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val callExpression = element.parentOfType<KtCallExpression>() ?: return
        analyze(callExpression) {
            val flowOnCallOrNull = callExpression.findFlowOnCall()
            val ktPsiFactory = KtPsiFactory(project, true)

            if (flowOnCallOrNull == null) {
                val callWithFlowReceiver = callExpression.parentOfType<KtCallExpression>() ?: return
                val resolvedCall = callWithFlowReceiver.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return
                if (!CoroutineBlockingCallInspectionUtils.isInsideFlowChain(resolvedCall)) return

                val dotQualifiedParent = callWithFlowReceiver.parentOfType<KtDotQualifiedExpression>()
                val refactoredElement =
                    if (dotQualifiedParent == null) {
                        val flowOnExpression =
                            ktPsiFactory.createExpression("${callWithFlowReceiver.text}\n.flowOn(kotlinx.coroutines.Dispatchers.IO)")
                        callWithFlowReceiver.replaced(flowOnExpression)
                    } else {
                        val flowOnExpression =
                            ktPsiFactory.createExpression("${dotQualifiedParent.text}\n.flowOn(kotlinx.coroutines.Dispatchers.IO)")
                        dotQualifiedParent.replaced(flowOnExpression)
                    }

                addImportExplicitly(refactoredElement.containingKtFile, CoroutineBlockingCallInspectionUtils.FLOW_ON_FQN)
                CoroutineBlockingCallInspectionUtils.postProcessQuickFix(refactoredElement, project)
            } else {
                val replacedArgument = flowOnCallOrNull.getFirstArgumentExpression()
                    ?.replaced(ktPsiFactory.createExpression("kotlinx.coroutines.Dispatchers.IO")) ?: return
                CoroutineBlockingCallInspectionUtils.postProcessQuickFix(replacedArgument, project)
            }
        }
    }

    private fun addImportExplicitly(file: KtFile, fqnToImport: FqName) {
        // TODO Use Import insertion API after KTIJ-28838 is fixed
        file.addImport(fqnToImport)
    }
}