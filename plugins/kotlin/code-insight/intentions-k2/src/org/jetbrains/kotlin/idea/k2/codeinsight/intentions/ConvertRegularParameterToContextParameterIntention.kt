// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.findValueParameterInChangeInfo
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.isValueParameterConvertibleToContext
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.runChangeSignatureForParameter
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.psi.KtParameter

class ConvertRegularParameterToContextParameterIntention : SelfTargetingIntention<KtParameter>(
    KtParameter::class.java,
    KotlinBundle.messagePointer("convert.value.parameter.to.context.parameter"),
), LowPriorityAction {
    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtParameter, caretOffset: Int): Boolean {
        return isValueParameterConvertibleToContext(element)
    }

    override fun applyTo(element: KtParameter, editor: Editor?) {
        runChangeSignatureForParameter(element) { changeInfo ->
            configureChangeInfo(element, changeInfo)
        }
    }

    private fun configureChangeInfo(element: KtParameter, changeInfo: KotlinChangeInfo): Boolean {
        val changedParameter = findValueParameterInChangeInfo(element, changeInfo) ?: return false
        changedParameter.isContextParameter = true
        return true
    }
}
