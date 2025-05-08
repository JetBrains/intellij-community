// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class ConvertContextParameterToRegularParameterIntention : SelfTargetingIntention<KtParameter>(
    KtParameter::class.java, KotlinBundle.lazyMessage("convert.to.regular.parameter")
) {
    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtParameter, caretOffset: Int): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        val contextParameterList = element.parent as? KtContextReceiverList ?: return false
        val ownerFunction = contextParameterList.ownerDeclaration as? KtNamedFunction ?: return false

        // conservatively avoid overrides for now
        if (ownerFunction.isAbstract()
            || ownerFunction.hasModifier(KtTokens.OPEN_KEYWORD)
            || ownerFunction.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        ) return false

        return true
    }

    override fun applyTo(element: KtParameter, editor: Editor?) {
        val ktFunction = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val methodDescriptor = KotlinMethodDescriptor(ktFunction)
        val changeInfo = KotlinChangeInfo(methodDescriptor)
        val changedParameter = changeInfo.getNonReceiverParameters().find {
            it.isContextParameter && it.oldName == element.name
        } ?: return
        changedParameter.isContextParameter = false
        KotlinChangeSignatureProcessor(element.project, changeInfo).run()
    }
}
