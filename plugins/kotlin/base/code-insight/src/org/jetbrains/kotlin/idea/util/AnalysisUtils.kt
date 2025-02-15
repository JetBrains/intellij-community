// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.psi.KtProperty

fun KaSession.isBackingFieldRequired(property: KtProperty): Boolean {
    val getter = property.getter
    val resolvedGetter = getter?.symbol
    val setter = property.setter
    val resolvedSetter = setter?.symbol

    if (getter == null) return true
    if (property.isVar && setter == null) return true
    if (resolvedSetter != null && !setter.hasBody() && resolvedSetter.modality != KaSymbolModality.ABSTRACT) return true
    if (!getter.hasBody() && resolvedGetter?.modality != KaSymbolModality.ABSTRACT) return true

    return false
}