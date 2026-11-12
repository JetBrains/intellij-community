// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.intentions.contexts.ContextParameterUtils.findContextParameterInChangeInfo
import org.jetbrains.kotlin.idea.codeinsight.intentions.contexts.ContextParameterUtils.isConvertibleContextParameter
import org.jetbrains.kotlin.idea.codeinsight.intentions.contexts.ContextParameterUtils.overridesOnlyModifiableDeclarations
import org.jetbrains.kotlin.idea.codeinsight.intentions.contexts.ContextParameterUtils.runChangeSignatureForParameter
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class ConvertContextParameterToReceiverIntention : SelfTargetingIntention<KtParameter>(
    KtParameter::class.java, KotlinBundle.messagePointer("convert.context.parameter.to.receiver.parameter")
), LowPriorityAction {
    override fun startInWriteAction(): Boolean = false

    override fun visitTargetTypeOnlyOnce(): Boolean = true

    override fun isApplicableTo(element: KtParameter, caretOffset: Int): Boolean {
        if (!isConvertibleContextParameter(element)) return false
        val ownerDeclaration = element.getStrictParentOfType<KtCallableDeclaration>() ?: return false
        if (ownerDeclaration.receiverTypeReference != null) return false
        if (!ownerDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return true

        val editor = element.findExistingEditor() ?: return false
        return computeWithProgressIconIfNeeded(editor, caretOffset) {
            analyze(ownerDeclaration) {
                overridesOnlyModifiableDeclarations(ownerDeclaration)
            }
        }
    }

    override fun applyTo(element: KtParameter, editor: Editor?) {
        runChangeSignatureForParameter(element) { changeInfo ->
            configureChangeInfo(element, changeInfo)
        }
    }

    private fun configureChangeInfo(element: KtParameter, changeInfo: KotlinChangeInfo): Boolean {
        val changedParameter = findContextParameterInChangeInfo(element, changeInfo) ?: return false
        changedParameter.isContextParameter = false
        changeInfo.receiverParameterInfo = changedParameter
        return true
    }
}
