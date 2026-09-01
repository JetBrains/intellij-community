// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.intentions.contexts

import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionCall
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtContextParameterList
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

object ContextParameterUtils {
    /**
     * Checks if the given [KtParameter] is a context parameter that can be converted into a value parameter or receiver.
     */
    fun isConvertibleContextParameter(ktParameter: KtParameter): Boolean {
        if (!ktParameter.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        val contextParameterList = ktParameter.parent as? KtContextParameterList ?: return false
        val contextParameterListOwner = contextParameterList.ownerDeclaration
        return contextParameterListOwner is KtCallableDeclaration
    }

    /**
     * Checks if the given [KtParameter] is a value parameter that can be converted into a context parameter.
     * The owner function must be a named function with a name identifier.
     * Parameters of lambdas and anonymous functions are not allowed.
     */
    fun isValueParameterConvertibleToContext(ktParameter: KtParameter): Boolean {
        if (!ktParameter.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        val valueParameterList = ktParameter.parent as? KtParameterList ?: return false
        val ownerFunction = valueParameterList.ownerFunction ?: return false
        val namedFunction = ownerFunction as? KtNamedFunction ?: return false
        return namedFunction.nameIdentifier != null
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
        return takeIf { this is KtNamedFunction || this is KtProperty }?.contextParameters
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

    /**
     * Checks if the given call expression is a call to the `kotlin.context` function.
     */
    context(session: KaSession)
    fun isKotlinContextCall(call: KtCallExpression): Boolean {
        val symbol = call.calleeExpression?.mainReference?.resolveToSymbol() as? KaFunctionSymbol ?: return false
        return symbol.callableId?.asSingleFqName() == FqName("kotlin.context")
    }

    /**
     * Returns the subset of [contextParameters] that are actually consumed inside [body],
     * either via a direct reference to a context parameter or as an implicit context argument
     * of a resolved call.
     */
    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    fun consumedContextParameters(
        body: KtExpression,
        contextParameters: List<KaContextParameterSymbol>,
    ): Set<KaContextParameterSymbol> {
        val allParameters = contextParameters.toSet()
        if (allParameters.isEmpty()) return emptySet()

        // Cheap pre-filter: a *direct* reference to a context parameter must use its name.
        val parameterNames = allParameters.mapTo(mutableSetOf()) { it.name.asString() }

        val consumed = mutableSetOf<KaContextParameterSymbol>()

        body.forEachDescendantOfType<KtSimpleNameExpression> { node ->
            if (consumed.size == allParameters.size) return@forEachDescendantOfType

            // Only pay for resolveToSymbol() when the name can possibly match a parameter.
            if (node.getReferencedName() in parameterNames) {
                val direct = node.mainReference.resolveToSymbol()
                if (direct is KaContextParameterSymbol && direct in allParameters) {
                    consumed += direct
                    return@forEachDescendantOfType
                }
            }

            val simpleCall = node.resolveSuccessfulExpressionCall()?.simple
                ?: return@forEachDescendantOfType

            simpleCall.contextArguments.forEach { arg ->
                val symbol = (arg.unwrapSmartCasts() as? KaImplicitReceiverValue)?.symbol
                if (symbol is KaContextParameterSymbol && symbol in allParameters) {
                    consumed += symbol
                }
            }
        }
        return consumed
    }

}
