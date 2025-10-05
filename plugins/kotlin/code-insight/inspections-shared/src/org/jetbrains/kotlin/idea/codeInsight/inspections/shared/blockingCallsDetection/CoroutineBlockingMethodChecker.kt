// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodChecker
import com.intellij.codeInspection.blockingCallsDetection.ElementContext
import com.intellij.codeInspection.blockingCallsDetection.MethodContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.isCalledInsideNonIoContext
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.isInSuspendLambdaOrFunction
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.isInsideFlowChain
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.blockingCallsDetection.CoroutineBlockingCallInspectionUtils.isKotlinxOnClasspath
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.hasSuspendModifier
import org.jetbrains.uast.toUElement

internal class CoroutineBlockingMethodChecker : BlockingMethodChecker {
    override fun isApplicable(file: PsiFile): Boolean {
        if (file !is KtFile) return false

        val languageVersionSettings = getLanguageVersionSettings(file)
        return languageVersionSettings.supportsFeature(LanguageFeature.ReleaseCoroutines)
    }

    override fun isMethodNonBlocking(context: MethodContext): Boolean {
        val uMethod = context.element.toUElement()
        val sourcePsi = uMethod?.sourcePsi ?: return false
        return sourcePsi is KtNamedFunction && sourcePsi.modifierList?.hasSuspendModifier() == true
    }

    override fun getQuickFixesFor(elementContext: ElementContext): Array<LocalQuickFix> {
        val element = elementContext.element
        if (element !is KtCallExpression) return emptyArray()

        analyze(element) {
            val resolvedCall = element.parentOfType<KtCallExpression>()?.resolveToCall()?.successfulCallOrNull<KaCall>()

            return when {
                !isApplicable(element.containingFile) || !isKotlinxOnClasspath(element) -> emptyArray()
                resolvedCall != null && isCalledInsideNonIoContext(resolvedCall) && isInSuspendLambdaOrFunction(element) -> arrayOf(ChangeContextFix(), WrapInWithContextFix())
                resolvedCall != null && isInsideFlowChain(resolvedCall) -> arrayOf(FlowOnIoContextFix(), WrapInWithContextFix())
                isInSuspendLambdaOrFunction(element) -> arrayOf(WrapInWithContextFix())
                else -> emptyArray()
            }
        }
    }

    private fun getLanguageVersionSettings(psiElement: PsiElement): LanguageVersionSettings {
        return psiElement.module?.languageVersionSettings ?: psiElement.project.languageVersionSettings
    }
}