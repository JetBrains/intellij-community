// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.weighers

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.name.FqName

private typealias LangVersionPredicate = (LanguageVersionSettings) -> Boolean

/**
 * Weigher to "sunk down" so-called "soft-deprecated" methods in completion list.
 * These are methods which are not recommended to use, but don't have [Deprecated] annotation over them,
 * hence they are not covered by DeprecatedWeigher and need special handling.
 *
 * At the moment there is no way to distinguish the soft-deprecated method ([KT-54106](https://youtrack.jetbrains.com/issue/KT-54106/Provide-API-for-perpetual-soft-deprecation-and-endorsing-uses-of-more-appropriate-API) is the task for this).
 * Because of this, we have to specify them all here manually.
 * The logic of this class must be edited once the API mentioned in KT-54106 been emerged.
 */
object SoftDeprecationWeigher {
    // TODO(Roman.Golyshev): change to "kotlin.softDeprecationWeigher"
    const val WEIGHER_ID = "kotlin.unwantedElement"

    private val fqNameToPredicate: Map<FqName, LangVersionPredicate> = mapOf(
        // See https://youtrack.jetbrains.com/issue/KTIJ-16131
        FqName("kotlinx.coroutines.flow.Flow.collect") to {
            // See https://youtrack.jetbrains.com/issue/KTIJ-23178
            Registry.`is`("kotlin.deprioritize.flow.collect.in.completion")
        },
        // See https://youtrack.jetbrains.com/issue/KTIJ-19510
        FqName("kotlin.io.readLine") to { it.supportsNewReadLineFunction },
    )

    private val LanguageVersionSettings.supportsNewReadLineFunction: Boolean
        get() = apiVersion >= ApiVersion.KOTLIN_1_6

    fun isSoftDeprecatedFqName(fqName: FqName, languageSettings: LanguageVersionSettings): Boolean {
        val predicate = fqNameToPredicate[fqName] ?: return false
        return predicate(languageSettings)
    }
}