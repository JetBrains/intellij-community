// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceWithData

fun fetchReplaceWithPattern(
    symbol: KaDeclarationSymbol
): ReplaceWithData? {
    val annotation = symbol.annotations.find { it.classId?.asSingleFqName() == StandardNames.FqNames.deprecated } ?: return null
    val replaceWithValue =
        (annotation.arguments.find { it.name.asString() == "replaceWith" }?.expression as? KaAnnotationValue.NestedAnnotationValue)?.annotation
            ?: return null
    val pattern =
        ((replaceWithValue.arguments.find { it.name.asString() == "expression" }?.expression as? KaAnnotationValue.ConstantValue)?.value as? KaConstantValue.StringValue)?.value
            ?: return null
    val imports =
        (replaceWithValue.arguments.find { it.name.asString() == "imports" }?.expression as? KaAnnotationValue.ArrayValue)?.values?.mapNotNull { ((it as? KaAnnotationValue.ConstantValue)?.value as? KaConstantValue.StringValue)?.value }
            ?: emptyList()

    return ReplaceWithData(pattern, imports, true)
}
