// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtVariableDeclaration
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

    fun validate(name: String): Boolean {
        val identifier = Name.identifier(name)

        @OptIn(KaAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            if (hasConflict(identifier)) return false
        }

        return visibleDeclarationsContext.siblings(withItself = checkVisibleDeclarationsContext).none { declaration ->
            declaration.findDescendantOfType<KtNamedDeclaration> { it.isConflicting(identifier) } != null ||
                    hasConflictWithImplicitLambdaParameter(identifier, declaration)
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun hasConflictWithImplicitLambdaParameter(identifier: Name, declaration: PsiElement): Boolean {
        if (identifier != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return false
        return declaration.findDescendantOfType<KtFunctionLiteral> {
            !it.hasParameterSpecification() && allowAnalysisOnEdt {
                analyze(visibleDeclarationsContext) {
                    it.symbol.valueParameters.singleOrNull()?.isImplicitLambdaParameter == true
                }
            }
        } != null
    }

    @OptIn(KaExperimentalApi::class)
    private fun hasConflict(identifier: Name): Boolean = analyze(visibleDeclarationsContext) {
        when (target) {
            KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY, KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE, KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER, KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION -> {
                val scope = visibleDeclarationsContext.containingKtFile.scopeContext(visibleDeclarationsContext).compositeScope()
                val containingClassSymbol = lazy(LazyThreadSafetyMode.NONE) { visibleDeclarationsContext.containingClass()?.classSymbol }
                scope.callables(identifier).filterIsInstance<KaVariableSymbol>().any {
                    it.psi !in excludedDeclarations && !it.isExtension && (containingClassSymbol.value?.let { cl ->
                        it.isVisibleInClass(cl)
                    } != false)
                }
            }

            KotlinNameSuggestionProvider.ValidatorTarget.CLASS -> {
                val scope = visibleDeclarationsContext.containingKtFile.scopeContext(visibleDeclarationsContext).compositeScope()
                scope.classifiers(identifier).any { it.psi !in excludedDeclarations }
            }
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