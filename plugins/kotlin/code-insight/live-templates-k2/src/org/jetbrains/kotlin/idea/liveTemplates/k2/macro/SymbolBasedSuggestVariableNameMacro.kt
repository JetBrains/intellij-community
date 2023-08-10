// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
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
                    @OptIn(KtAllowAnalysisFromWriteAction::class)
                    allowAnalysisFromWriteAction {
                        analyze(initializer) {
                            val nameValidator = KotlinDeclarationNameValidator(
                                declaration,
                                false,
                                KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                                this
                            )

                            return ((defaultName?.let { sequenceOf(it) } ?: emptySequence()) + NAME_SUGGESTER.suggestExpressionNames(initializer))
                                .filter(nameValidator)
                                .toList()
                        }
                    }
                }
            }
        }

        allowAnalysisOnEdt {
            @OptIn(KtAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(declaration) {
                    val symbol = declaration.getSymbolOfTypeSafe<KtVariableLikeSymbol>()
                    if (symbol != null) {
                        val nameValidator = KotlinDeclarationNameValidator(
                            declaration,
                            false,
                            KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                            this
                        )
                        return ((defaultName?.let { sequenceOf(it) } ?: emptySequence()) + NAME_SUGGESTER.suggestTypeNames(symbol.returnType))
                            .filter(nameValidator)
                            .toList()
                    }
                }
            }
        }

        return emptyList()
    }
}

private val NAME_SUGGESTER = KotlinNameSuggester()