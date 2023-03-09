// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.name.Name

@ApiStatus.Internal
val ENUM_VALUES_METHOD_NAME = Name.identifier("values")

@ApiStatus.Internal
fun KtAnalysisSession.isEnumValuesMethod(symbol: KtCallableSymbol): Boolean {
    return KtClassKind.ENUM_CLASS == (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind &&
            ENUM_VALUES_METHOD_NAME == symbol.callableIdIfNonLocal?.callableName &&
            // Don't touch user-declared methods with the name "values"
            symbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED
}

@ApiStatus.Internal
fun PsiElement.isEnumValuesSoftDeprecateEnabled(): Boolean = languageVersionSettings.isEnumValuesSoftDeprecateEnabled()

@ApiStatus.Internal
fun LanguageVersionSettings.isEnumValuesSoftDeprecateEnabled(): Boolean = supportsFeature(LanguageFeature.EnumEntries)
