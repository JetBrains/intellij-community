// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers

/**
 * Returns the [KtSymbolWithMembers] which contains the member declarations for this [KtDeclarationSymbol], or `null` if this symbol cannot
 * have any member declarations. This is usually the same symbol, but we have to make an exception for enum entries.
 *
 * A [KtClassOrObject][org.jetbrains.kotlin.psi.KtClassOrObject] might be an enum entry, but its symbol is not a [KtSymbolWithMembers],
 * because enum entry symbols are variable symbols. This is unfortunately a mismatch between the PSI
 * [KtEnumEntry][org.jetbrains.kotlin.psi.KtEnumEntry] on one side, and the Analysis API [KtEnumEntrySymbol] and the FIR compiler's view on
 * enum entries on the other side.
 *
 * For FE10, the enum entry's initializer symbol is equal to the enum entry symbol. If the enum entry doesn't have a body, its initializer
 * will be `null`, but the enum entry will also not contain any member declarations.
 */
fun KtDeclarationSymbol.getSymbolContainingMemberDeclarations(): KtSymbolWithMembers? = when (this) {
    is KtEnumEntrySymbol -> enumEntryInitializer
    is KtSymbolWithMembers -> this
    else -> null
}
