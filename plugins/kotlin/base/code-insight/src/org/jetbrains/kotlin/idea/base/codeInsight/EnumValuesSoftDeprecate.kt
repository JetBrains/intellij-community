// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.staticMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings

context(_: KaSession)
@ApiStatus.Internal
fun isSoftDeprecatedEnumValuesMethodAndEntriesPropertyExists(symbol: KaCallableSymbol): Boolean {
    val enumClassSymbol = (symbol.containingDeclaration as? KaClassSymbol) ?: return false
    return isSoftDeprecatedEnumValuesMethod(symbol, enumClassSymbol) &&
            getEntriesPropertyOfEnumClass(enumClassSymbol) != null
}

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

context(_: KaSession)
@ApiStatus.Internal
fun getEntriesPropertyOfEnumClass(enumClassSymbol: KaClassSymbol): KaCallableSymbol? =
    enumClassSymbol.staticMemberScope.callables(StandardNames.ENUM_ENTRIES).firstOrNull()

@ApiStatus.Internal
fun PsiElement.isEnumValuesSoftDeprecateEnabled(): Boolean = languageVersionSettings.isEnumValuesSoftDeprecateEnabled()

@ApiStatus.Internal
fun LanguageVersionSettings.isEnumValuesSoftDeprecateEnabled(): Boolean = supportsFeature(LanguageFeature.EnumEntries)
