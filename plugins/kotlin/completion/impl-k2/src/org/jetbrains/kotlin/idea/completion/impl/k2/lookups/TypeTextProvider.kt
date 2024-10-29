// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.completion.lookups.renderVerbose

internal object TypeTextProvider {
    /**
     * Creates lookup element's type text, based on provided classifier symbol.
     */
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getTypeTextForClassifier(symbol: KaClassifierSymbol): String? = when (symbol) {
        is KaTypeAliasSymbol -> symbol.expandedType.renderVerbose()
        else -> null
    }

    /**
     * Creates lookup element's type text, based on provided callable signature.
     *
     * @param treatAsFunctionCall true if variable with functional type should be treated as function call, in the following example:
     * ```
     * fun Int.test(foo: Int.() -> Unit) {
     *     fo<caret>
     * }
     * ```
     * a lookup element `foo()` is suggested and its type text should be `Unit`.
     */
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getTypeTextForCallable(
        signature: KaCallableSignature<*>,
        treatAsFunctionCall: Boolean
    ): String {
        val type = signature.returnType
        val typeToRender = when (signature) {
            is KaFunctionSignature<*> -> type.takeUnless { it is KaErrorType }
                ?: signature.symbol.returnType

            is KaVariableSignature<*> -> when {
                treatAsFunctionCall && type is KaFunctionType -> type.returnType
                else -> type
            }
        }

        return typeToRender.renderVerbose()
    }
}