// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts

import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

object ContextParameterUtils {
    /**
     * Checks if the given [KtParameter] is a context parameter that can be converted into a value parameter or receiver.
     */
    fun isConvertibleContextParameter(ktParameter: KtParameter): Boolean {
        if (!ktParameter.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        val contextParameterList = ktParameter.parent as? KtContextReceiverList ?: return false
        val contextParameterListOwner = contextParameterList.ownerDeclaration
        return contextParameterListOwner is KtCallableDeclaration
    }

    /**
     * Checks if the given [KtParameter] is a value parameter that can be converted into a context parameter.
     */
    fun isValueParameterConvertibleToContext(ktParameter: KtParameter): Boolean {
        if (!ktParameter.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        val valueParameterList = ktParameter.parent as? KtParameterList ?: return false
        val ownerFunction = valueParameterList.ownerFunction
        return ownerFunction != null && ownerFunction !is KtConstructor<*>
    }

    /**
     * Creates and configures [KotlinChangeInfo] using the owner callable of the [ktParameter] or the base overridden declaration for overrides.
     * The Change Signature refactoring runs with this info if [configureChangeInfo] returns `true`.
     * If [runAfterChangeSignature] is passed, it is called in the same action after the main refactoring is performed.
     */
    fun runChangeSignatureForParameter(
        ktParameter: KtParameter,
        runAfterChangeSignature: (() -> Unit)? = null,
        configureChangeInfo: (KotlinChangeInfo) -> Boolean,
    ) {
        val ktCallable = ktParameter.getStrictParentOfType<KtCallableDeclaration>() ?: return
        val changeInfo = createChangeInfo(ktCallable) ?: return
        if (!configureChangeInfo(changeInfo)) return

        val changeSignatureProcessor = object : KotlinChangeSignatureProcessor(ktParameter.project, changeInfo) {
            override fun performRefactoring(usages: Array<out UsageInfo?>) {
                super.performRefactoring(usages)
                runAfterChangeSignature?.invoke()
            }
        }
        changeSignatureProcessor.prepareSuccessfulSwingThreadCallback = Runnable { }
        changeSignatureProcessor.run()
    }

    /**
     * Finds context parameter in the given [changeInfo] by the parameter name and index.
     */
    fun findContextParameterInChangeInfo(ktParameter: KtParameter, changeInfo: KotlinChangeInfo): KotlinParameterInfo? =
        changeInfo.getNonReceiverParameters().find {
            it.isContextParameter && it.oldName == ktParameter.name && it.oldIndex == ktParameter.parameterIndex()
        }

    /**
     * Finds value parameter in the given [changeInfo] by the parameter name.
     */
    fun findValueParameterInChangeInfo(ktParameter: KtParameter, changeInfo: KotlinChangeInfo): KotlinParameterInfo? =
        changeInfo.getNonReceiverParameters().find {
            !it.isContextParameter && it.oldName == ktParameter.name
        }

    /**
     * Utility for getting context parameters from a callable declaration.
     * Returns the list of context parameters if the declaration is a function or a property with context parameters, and null otherwise.
     *
     * The utility mitigates the awkward declaration of context parameters in the Kotlin PSI hierarchy.
     */
    fun KtCallableDeclaration.getContextParameters(): List<KtParameter>? {
        return when (this) {
            is KtNamedFunction -> contextReceiverList?.contextParameters()
            is KtProperty -> contextReceiverList?.contextParameters()
            else -> null
        }
    }

    /**
     * Creates a new unconfigured [KotlinChangeInfo] for the given [ktCallable] for running the Change Signature refactoring.
     * Returns the info for the base overridden declaration for overrides, or the declaration itself for non-overrides.
     * Returns `null` if the declaration is an override and the user cancels the action after the warning.
     */
    fun createChangeInfo(ktCallable: KtCallableDeclaration): KotlinChangeInfo? {
        val callableWithOverridden = checkSuperMethods(ktCallable, emptyList(), RefactoringBundle.message("to.refactor"))
        val rootOverriddenOrSelf = callableWithOverridden.lastIsInstanceOrNull<KtCallableDeclaration>() ?: return null
        val methodDescriptor = KotlinMethodDescriptor(rootOverriddenOrSelf)
        return KotlinChangeInfo(methodDescriptor)
    }

    /**
     * Checks if the given [KtParameter] is anonymous, i.e., contains only underscore characters in its name.
     */
    fun isAnonymousParameter(ktParameter: KtParameter): Boolean =
        ktParameter.name.let { name -> !name.isNullOrEmpty() && name.all { char -> char == '_' }}
}
