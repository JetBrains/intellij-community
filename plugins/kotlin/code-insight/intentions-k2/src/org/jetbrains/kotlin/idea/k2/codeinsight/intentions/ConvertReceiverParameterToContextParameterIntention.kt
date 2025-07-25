// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.createChangeInfo
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.getContextParameters
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.renameParameter
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class ConvertReceiverParameterToContextParameterIntention : SelfTargetingIntention<KtTypeReference>(
    KtTypeReference::class.java,
    KotlinBundle.lazyMessage("convert.receiver.parameter.to.context.parameter"),
), LowPriorityAction {
    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtTypeReference, caretOffset: Int): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        return (element.parent as? KtNamedFunction)?.receiverTypeReference == element // disabled for properties, TODO KTIJ-34531
    }

    override fun applyTo(element: KtTypeReference, editor: Editor?) {
        val ktCallable = element.getStrictParentOfType<KtCallableDeclaration>() ?: return
        val changeInfo = createChangeInfo(ktCallable) ?: return
        if (!configureChangeInfo(changeInfo)) return
        object : KotlinChangeSignatureProcessor(element.project, changeInfo) {
            override fun performRefactoring(usages: Array<out UsageInfo?>) {
                super.performRefactoring(usages)
                DumbService.getInstance(element.project).smartInvokeLater {
                    renameLastContextParameter(ktCallable, editor)
                }
            }
        }.also {
            it.prepareSuccessfulSwingThreadCallback = Runnable { }
        }.run()
    }

    private fun configureChangeInfo(changeInfo: KotlinChangeInfo): Boolean {
        val oldReceiverInfo = changeInfo.oldReceiverInfo ?: return false
        changeInfo.receiverParameterInfo = null
        oldReceiverInfo.isContextParameter = true
        return true
    }

    private fun renameLastContextParameter(ktCallable: KtCallableDeclaration, editor: Editor?) {
        if (!ktCallable.isValid || editor == null || editor.isDisposed) return
        val lastContextParameter = ktCallable.getContextParameters()?.lastOrNull() ?: return
        renameParameter(lastContextParameter, editor)
    }
}
