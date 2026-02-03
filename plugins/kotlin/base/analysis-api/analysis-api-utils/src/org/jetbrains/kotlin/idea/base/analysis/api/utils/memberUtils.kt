// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol

/**
 * Returns the [KaDeclarationContainerSymbol] which contains the member declarations for this [KaDeclarationSymbol], or `null` if this symbol cannot
 * have any member declarations. This is usually the same symbol, but we have to make an exception for enum entries.
 *
 * A [KtClassOrObject][org.jetbrains.kotlin.psi.KtClassOrObject] might be an enum entry, but its symbol is not a [KaDeclarationContainerSymbol],
 * because enum entry symbols are variable symbols. This is unfortunately a mismatch between the PSI
 * [KtEnumEntry][org.jetbrains.kotlin.psi.KtEnumEntry] on one side, and the Analysis API [KaEnumEntrySymbol] and the FIR compiler's view on
 * enum entries on the other side.
 *
 * For FE10, the enum entry's initializer symbol is equal to the enum entry symbol. If the enum entry doesn't have a body, its initializer
 * will be `null`, but the enum entry will also not contain any member declarations.
 */
fun KaDeclarationSymbol.getSymbolContainingMemberDeclarations(): KaDeclarationContainerSymbol? = when (this) {
    is KaEnumEntrySymbol -> enumEntryInitializer
    is KaDeclarationContainerSymbol -> this
    else -> null
}
