/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter

context(KtAnalysisSession)
fun KtType.isNullableAnyType() = isAny && isMarkedNullable

context(KtAnalysisSession)
fun KtType.isNonNullableBooleanType() = isBoolean && !isMarkedNullable

context(KtAnalysisSession)
fun KtType.isEnum(): Boolean {
    if (this !is KtNonErrorClassType) return false
    val classSymbol = classSymbol
    return classSymbol is KtClassOrObjectSymbol && classSymbol.classKind == KtClassKind.ENUM_CLASS
}

/**
 * Always renders flexible type as its upper bound.
 *
 * TODO should be moved to [KtFlexibleTypeRenderer] and removed from here, see KT-64138
 */
object KtFlexibleTypeAsUpperBoundRenderer : KtFlexibleTypeRenderer {
    context(KtAnalysisSession, KtTypeRenderer)
    override fun renderType(type: KtFlexibleType, printer: PrettyPrinter) {
        renderType(type.upperBound, printer)
    }
}
