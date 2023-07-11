// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings

class KotlinDeclarationNameValidator(
    private val visibleDeclarationsContext: KtElement,
    private val checkDeclarationsIn: Sequence<PsiElement>,
    private val target: KotlinNameSuggestionProvider.ValidatorTarget,
    private val analysisSession: KtAnalysisSession
) : (String) -> Boolean {

    constructor(
        visibleDeclarationsContext: KtElement,
        target: KotlinNameSuggestionProvider.ValidatorTarget,
        analysisSession: KtAnalysisSession
    ) : this(
        visibleDeclarationsContext,
        visibleDeclarationsContext.siblings(withItself = false),
        target,
        analysisSession
    )

    init {
        check(
            target == KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY ||
                    target == KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE ||
                    target == KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER
        ) {
            "Unsupported yet target $target"
        }
    }

    override fun invoke(name: String): Boolean {
        val identifier = Name.identifier(name)

        if (analysisSession.hasConflict(identifier)) return false

        return checkDeclarationsIn.none { declaration ->
            declaration.findDescendantOfType<KtNamedDeclaration> { it.isConflicting(identifier) } != null
        }
    }

    private fun KtAnalysisSession.hasConflict(identifier: Name): Boolean {
        return when(target) {
            KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY, KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE, KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER -> {
                val scope =
                    visibleDeclarationsContext.containingKtFile.getScopeContextForPosition(visibleDeclarationsContext).getCompositeScope()
                scope.getCallableSymbols(identifier).any()
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