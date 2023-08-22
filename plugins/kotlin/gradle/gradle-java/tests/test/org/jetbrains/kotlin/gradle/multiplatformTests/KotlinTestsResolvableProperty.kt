// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import com.intellij.testFramework.UsefulTestCase

/**
 * Models special test properties that define test parameterizations on CI.
 * They can be read from the environment in form of `$ID = $acronym`, and also
 * will be substituted in the testdata with the pattern {{$id}}
 *
 * Unless you're changing CI-runs matrix, you should use simpler
 * [org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.SimpleProperties]
 */
interface KotlinTestsResolvableProperty {
    val id: String
    val valuesByAcronyms: Map<String, String>
    val defaultValue: String
}

fun KotlinTestsResolvableProperty.resolveFromEnvironment(): String {
    val acronymOrValue = System.getenv()[id.uppercase()] ?:
        if (!UsefulTestCase.IS_UNDER_TEAMCITY)
            return defaultValue
        else
            error("Error: can't find environment variable ${id.uppercase()} required for CI runs")


    if (acronymOrValue in valuesByAcronyms.keys) return valuesByAcronyms[acronymOrValue]!!
    if (acronymOrValue in valuesByAcronyms.values) return acronymOrValue

    val availablePropertyValues = valuesByAcronyms.entries.joinToString(separator = ", ") { (acronym, value) -> "$acronym ($value)" }
    error("Found value $acronymOrValue for environment variable $id, but it's not a valid acronym or value for this property\n" +
                  "Available values: $availablePropertyValues")
}
