// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findTopLevelCallables
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.codeInsight.getEntriesPropertyOfEnumClass
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesFunctionCall
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.base.codeInsight.isSoftDeprecatedEnumValuesMethod
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.completion.implCommon.weighers.SoftDeprecationWeigher
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds.BASE_ENUMS_PACKAGE
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object K2SoftDeprecationWeigher {

    private var LookupElement.isSoftDeprecated: Boolean
            by NotNullableUserDataProperty(Key("KOTLIN_SOFT_DEPRECATED"), false)

    context(_: KaSession)
    fun addWeight(
        lookupElement: LookupElement,
        symbol: KaCallableSymbol,
        languageVersionSettings: LanguageVersionSettings,
    ) {
        lookupElement.isSoftDeprecated = isLibrarySoftDeprecatedMethod(symbol, languageVersionSettings)
                || isEnumValuesSoftDeprecatedMethod(symbol, languageVersionSettings)
    }

    private fun isLibrarySoftDeprecatedMethod(
        symbol: KaCallableSymbol,
        languageVersionSettings: LanguageVersionSettings,
    ): Boolean {
        val fqName = symbol.callableId?.asSingleFqName()
        return fqName != null && SoftDeprecationWeigher.isSoftDeprecatedFqName(fqName, languageVersionSettings)
    }

    /**
     * Lower soft-deprecated `Enum.values()` and 'enumValues<*>()' methods in completion.
     * See [KT-22298](https://youtrack.jetbrains.com/issue/KTIJ-22298/Soft-deprecate-Enumvalues-for-Kotlin-callers)
     * and [KT-61120](https://youtrack.jetbrains.com/issue/KT-61120/Consider-deprecating-or-decommisioning-enumValues-intrinsic-in-the-favor-of-enumEntries).
     */
    context(_: KaSession)
    private fun isEnumValuesSoftDeprecatedMethod(
        symbol: KaCallableSymbol,
        languageVersionSettings: LanguageVersionSettings,
    ): Boolean = (languageVersionSettings.isEnumValuesSoftDeprecateEnabled() && canDeprecateValuesCallOnEnum(symbol)) || canDeprecateEnumValuesTopLevel(symbol)

    context(_: KaSession)
    private fun canDeprecateValuesCallOnEnum(symbol: KaCallableSymbol): Boolean {
        val enumClassSymbol = (symbol.containingDeclaration as? KaClassSymbol) ?: return false
        return (isSoftDeprecatedEnumValuesMethod(symbol, enumClassSymbol) && getEntriesPropertyOfEnumClass(enumClassSymbol) != null)
    }

    context(_: KaSession)
    private fun canDeprecateEnumValuesTopLevel(symbol: KaCallableSymbol): Boolean {
        val enumValuesCallable = CallableId(BASE_ENUMS_PACKAGE, StandardKotlinNames.Enum.enumEntries.shortName())
        return isEnumValuesFunctionCall(symbol) && findTopLevelCallables(enumValuesCallable.packageName, enumValuesCallable.callableName).any()
    }

    object Weigher : LookupElementWeigher(SoftDeprecationWeigher.WEIGHER_ID) {

        override fun weigh(
            element: LookupElement,
            context: WeighingContext,
        ): Boolean = element.isSoftDeprecated
    }
}