// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.inspections.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.findFlowOnCall
import org.jetbrains.kotlin.idea.util.ImportInsertHelperImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.callUtil.getFirstArgumentExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode


internal class FlowOnIoContextFix : LocalQuickFix {
    override fun getFamilyName(): String {
        return KotlinBundle.message("intention.flow.on.dispatchers.io")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val callExpression = descriptor.psiElement?.parentOfType<KtCallExpression>() ?: return
        val flowOnCallOrNull = callExpression.findFlowOnCall()
        val ktPsiFactory = KtPsiFactory(project, true)

        if (flowOnCallOrNull == null) {
            val callWithFlowReceiver = callExpression.parentOfType<KtCallExpression>() ?: return
            val resolvedCall = callWithFlowReceiver.resolveToCall(BodyResolveMode.PARTIAL) ?: return
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

            addImportExplicitly(project, refactoredElement.containingKtFile, "kotlinx.coroutines.flow.flowOn")
            CoroutineBlockingCallInspectionUtils.postProcessQuickFix(refactoredElement, project)
        } else {
            val replacedArgument = flowOnCallOrNull.getFirstArgumentExpression()
                ?.replaced(ktPsiFactory.createExpression("kotlinx.coroutines.Dispatchers.IO")) ?: return
            CoroutineBlockingCallInspectionUtils.postProcessQuickFix(replacedArgument, project)
        }
    }

    private fun addImportExplicitly(project: Project, file: KtFile, @Suppress("SameParameterValue") fqnToImport: String) {
        ImportInsertHelperImpl.addImport(project, file, FqName(fqnToImport))
    }
}