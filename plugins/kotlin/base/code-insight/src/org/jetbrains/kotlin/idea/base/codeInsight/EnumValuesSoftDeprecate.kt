// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings

context(KtAnalysisSession)
@ApiStatus.Internal
fun isSoftDeprecatedEnumValuesMethodAndEntriesPropertyExists(symbol: KtCallableSymbol): Boolean {
    val enumClassSymbol = (symbol.getContainingSymbol() as? KtClassOrObjectSymbol) ?: return false
    return isSoftDeprecatedEnumValuesMethod(symbol, enumClassSymbol) &&
            getEntriesPropertyOfEnumClass(enumClassSymbol) != null
}

@ApiStatus.Internal
fun isSoftDeprecatedEnumValuesMethod(
    valuesMethodSymbol: KtCallableSymbol,
    enumClassSymbol: KtClassOrObjectSymbol,
): Boolean {
    return KtClassKind.ENUM_CLASS == enumClassSymbol.classKind &&
            StandardNames.ENUM_VALUES == valuesMethodSymbol.callableIdIfNonLocal?.callableName &&
            // Don't touch user-declared methods with the name "values"
            valuesMethodSymbol is KtFunctionLikeSymbol && valuesMethodSymbol.valueParameters.isEmpty()
}

context(KtAnalysisSession)
@ApiStatus.Internal
fun getEntriesPropertyOfEnumClass(enumClassSymbol: KtClassOrObjectSymbol): KtCallableSymbol? =
    enumClassSymbol.getStaticMemberScope().getCallableSymbols(StandardNames.ENUM_ENTRIES).firstOrNull()

@ApiStatus.Internal
fun PsiElement.isEnumValuesSoftDeprecateEnabled(): Boolean = languageVersionSettings.isEnumValuesSoftDeprecateEnabled()

@ApiStatus.Internal
fun LanguageVersionSettings.isEnumValuesSoftDeprecateEnabled(): Boolean = supportsFeature(LanguageFeature.EnumEntries)
