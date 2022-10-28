package org.jetbrains.kotlin.idea.completion.implCommon.weighers

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.name.Name

/**
 * Weigher to lower soft-deprecated [Enum.values] method in completion.
 * See [KT-22298](https://youtrack.jetbrains.com/issue/KTIJ-22298/Soft-deprecate-Enumvalues-for-Kotlin-callers).
 */
object EnumValuesSoftDeprecationWeigher {
    val VALUES_METHOD_NAME = Name.identifier("values")

    fun weigherIsEnabled(languageVersionSettings: LanguageVersionSettings): Boolean {
        return languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)
    }
}