// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.liveTemplates.macro.AbstractSuggestVariableNameMacro
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer

class SymbolBasedSuggestVariableNameMacro(private val defaultName: String? = null) : AbstractSuggestVariableNameMacro() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun suggestNames(declaration: KtCallableDeclaration): Collection<String> {
        if (declaration is KtDeclarationWithInitializer) {
            val initializer = declaration.initializer
            if (initializer != null) {
                allowAnalysisOnEdt {
                    @OptIn(KaAllowAnalysisFromWriteAction::class)
                    allowAnalysisFromWriteAction {
                        analyze(initializer) {
                            val nameValidator = KotlinDeclarationNameValidator(
                              declaration,
                              false,
                              KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                              listOf(declaration)
                            )

                            return (sequenceOfNotNull (defaultName) + NAME_SUGGESTER.suggestExpressionNames(initializer))
                                .filter { nameValidator.validate(it) }
                                .toList()
                        }
                    }
                }
            }
        }

        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(declaration) {
                    val symbol = declaration.symbol as? KaVariableSymbol
                    if (symbol != null) {
                        val nameValidator = KotlinDeclarationNameValidator(
                          declaration,
                          false,
                          KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                          listOf(declaration)
                        )
                        return (sequenceOfNotNull (defaultName) + NAME_SUGGESTER.suggestTypeNames(symbol.returnType))
                            .filter { nameValidator.validate(it) }
                            .toList()
                    }
                }
            }
        }

        return emptyList()
    }
}

private val NAME_SUGGESTER = KotlinNameSuggester()