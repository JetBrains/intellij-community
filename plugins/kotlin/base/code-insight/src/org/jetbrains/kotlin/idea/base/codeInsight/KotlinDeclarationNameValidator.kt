// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.siblings

@ApiStatus.Internal
class KotlinDeclarationNameValidator(
  private val visibleDeclarationsContext: KtElement,
  private val checkVisibleDeclarationsContext: Boolean,
  private val target: KotlinNameSuggestionProvider.ValidatorTarget,
  private val excludedDeclarations: List<KtDeclaration> = emptyList(),
) {

    init {
        check(
          target == KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY ||
          target == KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE ||
          target == KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER ||
          target == KotlinNameSuggestionProvider.ValidatorTarget.CLASS ||
          target == KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION
        ) {
            "Unsupported yet target $target"
        }
    }


    context(KaSession)
    fun validate(name: String): Boolean {
        val identifier = Name.identifier(name)

        @OptIn(KaAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            if (hasConflict(identifier)) return false
        }

        return visibleDeclarationsContext.siblings(withItself = checkVisibleDeclarationsContext).none { declaration ->
            declaration.findDescendantOfType<KtNamedDeclaration> { it.isConflicting(identifier) } != null
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun hasConflict(identifier: Name): Boolean {
        return when(target) {
            KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY, KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE, KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER, KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION -> {
                val scope =
                    visibleDeclarationsContext.containingKtFile.scopeContext(visibleDeclarationsContext).compositeScope()
                val containingClassSymbol = lazy(LazyThreadSafetyMode.NONE) { visibleDeclarationsContext.containingClass()?.classSymbol }
                scope.callables(identifier).filterIsInstance<KaVariableSymbol>().any {
                    it.psi !in excludedDeclarations && !it.isExtension && (containingClassSymbol.value?.let { cl -> it.isVisibleInClass(cl) } != false)
                }
            }
            KotlinNameSuggestionProvider.ValidatorTarget.CLASS -> {
                val scope =
                  visibleDeclarationsContext.containingKtFile.scopeContext(visibleDeclarationsContext).compositeScope()
                scope.classifiers(identifier).any { it.psi !in excludedDeclarations }
            }
            else -> false
        }
    }

    private fun KtNamedDeclaration.isConflicting(name: Name): Boolean {
        if (this in excludedDeclarations) return false
        if (nameAsName != name) return false
        if (this is KtCallableDeclaration && receiverTypeReference != null) return false

        if (this is KtParameter && !visibleDeclarationsContext.isAncestor(this)) {
            return false
        }

        return when (target) {
            KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY, KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER, KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE ->
                this is KtVariableDeclaration || this is KtParameter
            KotlinNameSuggestionProvider.ValidatorTarget.CLASS, KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION ->
                this is KtNamedFunction || this is KtClassOrObject || this is KtTypeAlias
        }
    }
}