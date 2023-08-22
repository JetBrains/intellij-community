// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings

class KotlinDeclarationNameValidator(
    private val visibleDeclarationsContext: KtElement,
    private val checkVisibleDeclarationsContext: Boolean,
    private val target: KotlinNameSuggestionProvider.ValidatorTarget,
    private val analysisSession: KtAnalysisSession
) : (String) -> Boolean {

    init {
        check(
          target == KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY ||
          target == KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE ||
          target == KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER ||
          target == KotlinNameSuggestionProvider.ValidatorTarget.CLASS
        ) {
            "Unsupported yet target $target"
        }
    }


    override fun invoke(name: String): Boolean {
        val identifier = Name.identifier(name)

        @OptIn(KtAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            with(analysisSession) {
                if (hasConflict(identifier)) return false
            }
        }

        return visibleDeclarationsContext.siblings(withItself = checkVisibleDeclarationsContext).none { declaration ->
            declaration.findDescendantOfType<KtNamedDeclaration> { it.isConflicting(identifier) } != null
        }
    }

    context(KtAnalysisSession)
    private fun hasConflict(identifier: Name): Boolean {
        return when(target) {
            KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY, KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE, KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER -> {
                val scope =
                    visibleDeclarationsContext.containingKtFile.getScopeContextForPosition(visibleDeclarationsContext).getCompositeScope()
                val containingClassSymbol = lazy(LazyThreadSafetyMode.NONE) { visibleDeclarationsContext.containingClass()?.getClassOrObjectSymbol() }
                scope.getCallableSymbols(identifier).filterIsInstance<KtVariableLikeSymbol>().any {
                    !it.isExtension && (containingClassSymbol.value?.let { cl -> it.isVisibleInClass(cl) } != false)
                }
            }
            KotlinNameSuggestionProvider.ValidatorTarget.CLASS -> {
                val scope =
                  visibleDeclarationsContext.containingKtFile.getScopeContextForPosition(visibleDeclarationsContext).getCompositeScope()
                scope.getClassifierSymbols(identifier).any()
            }
            else -> false
        }
    }

    private fun KtNamedDeclaration.isConflicting(name: Name): Boolean {
        if (nameAsName != name) return false
        if (this is KtCallableDeclaration && receiverTypeReference != null) return false

        return when (target) {
            KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY, KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER, KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE ->
                this is KtVariableDeclaration || this is KtParameter
            KotlinNameSuggestionProvider.ValidatorTarget.CLASS, KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION ->
                this is KtNamedFunction || this is KtClassOrObject || this is KtTypeAlias
        }
    }
}