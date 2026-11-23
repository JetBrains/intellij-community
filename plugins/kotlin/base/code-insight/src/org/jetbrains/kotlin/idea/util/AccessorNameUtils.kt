// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.javaInterop.javaMethodName
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtPsiUtil
import java.util.Collections

/**
 * Takes getter and setter names for a property. If there are no getters/setters, returns an empty list.
 * A `const` or a [JvmField] property has no accessor methods, so the function returns the property name.
 * Java code refers to the field of such a property by this name.
 */
@ApiStatus.Internal
fun KtCallableDeclaration.getAccessorNames(): List<String> {

    val ktDeclaration = this

    if (KtPsiUtil.isLocal(ktDeclaration)) return Collections.emptyList()

    analyze(ktDeclaration) {
        val symbol = ktDeclaration.symbol
        val probablePropertySymbol = if (symbol is KaValueParameterSymbol) {
            symbol.primaryConstructorProperty
        } else {
            symbol
        }
        if (probablePropertySymbol !is KaPropertySymbol) return emptyList()

        if (probablePropertySymbol is KaKotlinPropertySymbol && probablePropertySymbol.isConst ||
            probablePropertySymbol.backingFieldSymbol?.annotations?.contains(JvmStandardClassIds.Annotations.JvmField) == true
        ) {
            return listOf(probablePropertySymbol.name.asString())
        }

        return listOfNotNull(
            probablePropertySymbol.getter?.javaMethodName,
            probablePropertySymbol.setter?.javaMethodName
        )
    }
}
