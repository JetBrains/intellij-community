// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse

object ContextParameterUtils {
    /**
     * Checks if the given [KtParameter] is a context parameter that can be converted to another kind of parameter.
     */
    fun isConvertibleContextParameter(ktParameter: KtParameter): Boolean {
        if (!ktParameter.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        val contextParameterList = ktParameter.parent as? KtContextReceiverList ?: return false
        val contextParameterListOwner = contextParameterList.ownerDeclaration
        // Change Signature support for context properties is required KTIJ-34042
        if (contextParameterListOwner !is KtNamedFunction) return false
        if (contextParameterListOwner.isOpenAbstractOrOverride()) return false

        return true
    }

    fun isValueParameterConvertibleToContext(ktParameter: KtParameter): Boolean {
        if (!ktParameter.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        val valueParameterList = ktParameter.parent as? KtParameterList ?: return false
        val owner = valueParameterList.ownerFunction as? KtNamedFunction ?: return false
        if (owner.isOpenAbstractOrOverride()) return false

        return true
    }

    /**
     * Creates and configures [KotlinChangeInfo] using the owner function of the [element] for conversion intentions.
     * The Change Signature refactoring runs with this info if [configureChangeInfo] returns `true`.
     */
    fun runChangeSignatureForParameter(element: KtParameter, configureChangeInfo: (KotlinChangeInfo) -> Boolean) {
        val ktFunction = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val methodDescriptor = KotlinMethodDescriptor(ktFunction)
        val changeInfo = KotlinChangeInfo(methodDescriptor)
        configureChangeInfo(changeInfo).ifFalse { return }
        KotlinChangeSignatureProcessor(element.project, changeInfo).also {
            it.prepareSuccessfulSwingThreadCallback = Runnable { }
        }.run()
    }

    /**
     * Finds context parameter in the given [changeInfo] by the parameter name.
     */
    fun findContextParameterInChangeInfo(ktParameter: KtParameter, changeInfo: KotlinChangeInfo): KotlinParameterInfo? =
        changeInfo.getNonReceiverParameters().find {
            it.isContextParameter && it.oldName == ktParameter.name
        }

    /**
     * Finds value parameter in the given [changeInfo] by the parameter name.
     */
    fun findValueParameterInChangeInfo(ktParameter: KtParameter, changeInfo: KotlinChangeInfo): KotlinParameterInfo? =
        changeInfo.getNonReceiverParameters().find {
            !it.isContextParameter && it.oldName == ktParameter.name
        }

    // to avoid overrides and overridable declarations KTIJ-34463
    private fun KtDeclaration.isOpenAbstractOrOverride(): Boolean =
        isAbstract() || hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.OVERRIDE_KEYWORD)
}