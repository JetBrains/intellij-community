// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiDocumentManager
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinMemberInplaceRenameHandler
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class ConvertReceiverParameterToContextParameterIntention : SelfTargetingIntention<KtTypeReference>(
    KtTypeReference::class.java,
    KotlinBundle.lazyMessage("convert.receiver.parameter.to.context.parameter"),
), LowPriorityAction {
    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtTypeReference, caretOffset: Int): Boolean {
        return element.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)
    }

    override fun applyTo(element: KtTypeReference, editor: Editor?) {
        val ktFunction = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val methodDescriptor = KotlinMethodDescriptor(ktFunction)
        val changeInfo = KotlinChangeInfo(methodDescriptor)
        if (!configureChangeInfo(changeInfo)) return
        object : KotlinChangeSignatureProcessor(element.project, changeInfo) {
            override fun performRefactoring(usages: Array<out UsageInfo?>) {
                super.performRefactoring(usages)
                DumbService.getInstance(element.project).smartInvokeLater {
                    renameLastContextParameter(ktFunction, editor)
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

    private fun renameLastContextParameter(ktFunction: KtNamedFunction, editor: Editor?) {
        if (!ktFunction.isValid || editor == null || editor.isDisposed) return
        val lastContextParameter = ktFunction.contextReceiverList?.contextParameters()?.lastOrNull() ?: return
        editor.caretModel.moveToOffset(lastContextParameter.startOffset)
        PsiDocumentManager.getInstance(ktFunction.project).doPostponedOperationsAndUnblockDocument(editor.document)
        KotlinMemberInplaceRenameHandler().doRename(lastContextParameter, editor, null)
    }
}
