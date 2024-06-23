/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter

context(KaSession)
fun KtType.isNullableAnyType() = isAny && isMarkedNullable

context(KaSession)
fun KtType.isNonNullableBooleanType() = isBoolean && !isMarkedNullable

context(KaSession)
fun KtType.isEnum(): Boolean {
    if (this !is KtNonErrorClassType) return false
    val classSymbol = symbol
    return classSymbol is KaClassSymbol && classSymbol.classKind == KaClassKind.ENUM_CLASS
}

/**
 * Always renders flexible type as its upper bound.
 *
 * TODO should be moved to [KaFlexibleTypeRenderer] and removed from here, see KT-64138
 */
@KaExperimentalApi
object KtFlexibleTypeAsUpperBoundRenderer : KaFlexibleTypeRenderer {
    override fun renderType(
        analysisSession: KaSession,
        type: KtFlexibleType,
        typeRenderer: KtTypeRenderer,
        printer: PrettyPrinter
    ) {
        typeRenderer.renderType(analysisSession, type.upperBound, printer)
    }
}
