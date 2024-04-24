// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtPsiUtil
import java.util.*

/**
 * Takes getter and setter names for a property. If there are no getters/setters, returns an empty list.
 */
@ApiStatus.Internal
fun KtCallableDeclaration.getAccessorNames(): List<String> {

    val ktDeclaration = this

    if (KtPsiUtil.isLocal(ktDeclaration)) return Collections.emptyList()

    analyze(ktDeclaration) {
        val symbol = ktDeclaration.getSymbol()
        val probablePropertySymbol = if (symbol is KtValueParameterSymbol) {
            symbol.generatedPrimaryConstructorProperty
        } else {
            symbol
        }
        if (probablePropertySymbol !is KtPropertySymbol) return emptyList()

        return listOfNotNull(
            probablePropertySymbol.javaGetterName.identifier,
            probablePropertySymbol.javaSetterName?.identifier
        )
    }
}
