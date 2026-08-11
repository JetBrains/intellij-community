// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.scopes.staticMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.name.FqName


@ApiStatus.Internal
context(_: KaSession)
fun isEnumValuesFunctionCall(symbol: KaCallableSymbol): Boolean = symbol.callableId?.asSingleFqName() == FqName("kotlin.enumValues")

@ApiStatus.Internal
fun isSoftDeprecatedEnumValuesMethod(
    valuesMethodSymbol: KaCallableSymbol,
    enumClassSymbol: KaClassSymbol,
): Boolean {
    return KaClassKind.ENUM_CLASS == enumClassSymbol.classKind &&
            StandardNames.ENUM_VALUES == valuesMethodSymbol.callableId?.callableName &&
            // Don't touch user-declared methods with the name "values"
            valuesMethodSymbol is KaFunctionSymbol && valuesMethodSymbol.valueParameters.isEmpty()
}

@ApiStatus.Internal
context(_: KaSession)
fun getEntriesPropertyOfEnumClass(enumClassSymbol: KaClassSymbol): KaCallableSymbol? =
    enumClassSymbol.staticMemberScope.callables(StandardNames.ENUM_ENTRIES).firstOrNull()

@ApiStatus.Internal
fun PsiElement.isEnumValuesSoftDeprecateEnabled(): Boolean = languageVersionSettings.isEnumValuesSoftDeprecateEnabled()

@ApiStatus.Internal
fun LanguageVersionSettings.isEnumValuesSoftDeprecateEnabled(): Boolean = supportsFeature(LanguageFeature.EnumEntries)
