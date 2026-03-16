// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import com.intellij.testFramework.UsefulTestCase
import kotlin.enums.enumEntries

/**
 * Models special test properties that define test parameterizations on CI.
 * They can be read from the environment in form of `$ID = $alias`, and also
 * will be substituted in the testdata with the pattern {{$id}}
 *
 * Unless you're changing CI-runs matrix, you should use simpler
 * [org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.SimpleProperties]
 */

interface ValueFromEnvironment {
    val version: String
    // This alias is read from System.getenv() ans is associated with the respective version
    val versionAlias: String
}

interface KotlinTestsResolvableProperty <ValueT> where ValueT : Enum<ValueT>, ValueT: ValueFromEnvironment {
    val id: String
    val versionByAlias: Map<ValueT, String>
    val defaultValue: ValueT
}

inline fun <reified ValueT> KotlinTestsResolvableProperty<ValueT>.resolveFromEnvironment(): ValueT where ValueT : Enum<ValueT>, ValueT: ValueFromEnvironment {
    val aliasOrVersion = System.getenv()[id.uppercase()] ?:
        if (!UsefulTestCase.IS_UNDER_TEAMCITY)
            return defaultValue
        else
            error("Error: can't find environment variable ${id.uppercase()} required for CI runs")

    val alias = enumEntries<ValueT>().firstOrNull { it.versionAlias == aliasOrVersion }
    if (alias != null) return alias

    val reverseAliasFromVersion = versionByAlias.entries.firstOrNull { it.value == aliasOrVersion }
    if (reverseAliasFromVersion != null) return reverseAliasFromVersion.key

    val availablePropertyValues = versionByAlias.entries.joinToString(separator = ", ") { (alias, value) -> "${alias.versionAlias} ($value)" }
    error("Found value $aliasOrVersion for environment variable $id, but it's not a valid alias or value for this property\n" +
                  "Available values: $availablePropertyValues")
}
