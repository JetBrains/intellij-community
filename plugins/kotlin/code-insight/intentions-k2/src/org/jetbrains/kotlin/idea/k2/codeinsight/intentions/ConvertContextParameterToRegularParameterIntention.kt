// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Editor
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.createSmartPointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.findContextParameterInChangeInfo
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.isAnonymousParameter
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.isConvertibleContextParameter
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.contexts.ContextParameterUtils.runChangeSignatureForParameter
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.renameParameter
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class ConvertContextParameterToRegularParameterIntention : SelfTargetingIntention<KtParameter>(
    KtParameter::class.java, KotlinBundle.lazyMessage("convert.context.parameter.to.regular.parameter")
), LowPriorityAction {
    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtParameter, caretOffset: Int): Boolean {
        if (!isConvertibleContextParameter(element)) return false
        val ownerFunction = (element.parent as? KtContextReceiverList)?.ownerDeclaration as? KtNamedFunction ?: return false
        return !isAnonymousParameter(element) || !ownerFunction.hasModifier(OVERRIDE_KEYWORD) // KTIJ-34978
    }

    override fun applyTo(element: KtParameter, editor: Editor?) {
        val ownerFunction = (element.ownerDeclaration as? KtNamedFunction) ?: return
        val ownerFunctionPointer = ownerFunction.createSmartPointer()
        val isAnonymousParameter = isAnonymousParameter(element)

        val parameterPointer = element.createSmartPointer()
        if (isAnonymousParameter) {
            renameAnonymousContextParameter(element, ownerFunction)
        }

        val parameterToChange = parameterPointer.element ?: return
        runChangeSignatureForParameter(
            parameterToChange,
            configureChangeInfo = { changeInfo -> configureChangeInfo(parameterToChange, changeInfo) },
            runAfterChangeSignature = {
                if (isAnonymousParameter) {
                    ownerFunctionPointer.element?.let { function ->
                        renameFirstValueParameterInPlace(function, editor)
                    }
                }
            },
        )
    }

    private fun configureChangeInfo(element: KtParameter, changeInfo: KotlinChangeInfo): Boolean {
        val changedParameter = findContextParameterInChangeInfo(element, changeInfo) ?: return false
        changedParameter.isContextParameter = false
        return true
    }

    private fun renameAnonymousContextParameter(contextParameter: KtParameter, ownerFunction: KtNamedFunction) {
        runWithModalProgressBlocking(contextParameter.project, KotlinBundle.message("convert.context.parameter.to.regular.parameter.progress")) {
            var initialNameForAnonymousParameter: String? = null
            val contextParametersInOverrides = mutableListOf<KtParameter>()
            readAction {
                analyze(contextParameter) {
                    initialNameForAnonymousParameter = suggestParameterNameByType(contextParameter, ownerFunction)
                    if (ownerFunction.containingClassOrObject != null) {
                        collectContextParametersFromOverrides(contextParameter, ownerFunction, contextParametersInOverrides)
                    }
                }
            }

            initialNameForAnonymousParameter?.let { initialName ->
                withContext(Dispatchers.EDT) {
                    writeAction {
                        (contextParametersInOverrides + contextParameter).forEach { contextParameter ->
                            if (contextParameter.isWritable) {
                                contextParameter.setName(initialName)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun collectContextParametersFromOverrides(
        contextParameter: KtParameter,
        ownerFunction: KtNamedFunction,
        result: MutableList<KtParameter>,
    ) {
        val parameterIndex = contextParameter.parameterIndex().takeIf { it >= 0 } ?: return
        val overriders = KotlinFindUsagesSupport.searchOverriders(ownerFunction, ownerFunction.useScope).mapNotNull {
            (it as? KtNamedFunction)
        }
        overriders.mapNotNullTo(result) { ktFunction ->
            findContextParameterByIndex(ktFunction, parameterIndex)
        }
    }

    private fun findContextParameterByIndex(function: KtNamedFunction, index: Int): KtParameter? {
        if (!function.containingKtFile.isUnderKotlinSourceRootTypes()) return null
        return function.contextReceiverList?.contextParameters()?.getOrNull(index)
    }

    private fun KaSession.suggestParameterNameByType(ktParameter: KtParameter, ownerFunction: KtNamedFunction): String? {
        val type = ktParameter.symbol.returnType

        val nameValidator = KotlinDeclarationNameValidator(
            visibleDeclarationsContext = ownerFunction,
            checkVisibleDeclarationsContext = true,
            target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
        )

        return KotlinNameSuggester().suggestTypeNames(type).map { typeName ->
            KotlinNameSuggester.suggestNameByName(typeName, nameValidator::validate)
        }.firstOrNull()
    }

    private fun renameFirstValueParameterInPlace(ownerFunction: KtNamedFunction, editor: Editor?) {
        val firstValueParameter = ownerFunction.valueParameters.firstOrNull() ?: return
        if (!ownerFunction.isValid || editor == null || editor.isDisposed) return

        renameParameter(firstValueParameter, editor)
    }
}
