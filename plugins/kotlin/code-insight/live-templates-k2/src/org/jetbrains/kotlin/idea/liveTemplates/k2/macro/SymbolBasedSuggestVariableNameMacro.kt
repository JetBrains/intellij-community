// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfTypeSafe
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.liveTemplates.macro.AbstractSuggestVariableNameMacro
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer

class SymbolBasedSuggestVariableNameMacro(private val defaultName: String? = null) : AbstractSuggestVariableNameMacro() {
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun suggestNames(declaration: KtCallableDeclaration): Collection<String> {
        if (declaration is KtDeclarationWithInitializer) {
            val initializer = declaration.initializer
            if (initializer != null) {
                allowAnalysisOnEdt {
                    analyze(initializer) {
                        val nameValidator = KotlinDeclarationNameValidator(
                            declaration,
                            KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                            this
                        )

                        with(NAME_SUGGESTER) {
                            return ((defaultName?.let { sequenceOf(it) } ?: emptySequence()) + suggestExpressionNames(initializer))
                                .filter(nameValidator)
                                .toList()
                        }
                    }
                }
            }
        }

        allowAnalysisOnEdt {
            analyze(declaration) {
                val symbol = declaration.getSymbolOfTypeSafe<KtVariableLikeSymbol>()
                if (symbol != null) {
                    val nameValidator = KotlinDeclarationNameValidator(
                        declaration,
                        KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                        this
                    )
                    with(NAME_SUGGESTER) {
                        return ((defaultName?.let { sequenceOf(it) } ?: emptySequence()) + suggestTypeNames(symbol.returnType))
                            .filter(nameValidator)
                            .toList()
                    }
                }
            }
        }

        return emptyList()
    }

    private companion object {
        val NAME_SUGGESTER = KotlinNameSuggester(KotlinNameSuggester.Case.CAMEL)
    }
}