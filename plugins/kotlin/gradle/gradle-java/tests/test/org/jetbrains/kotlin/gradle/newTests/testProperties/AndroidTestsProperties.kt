// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testProperties

import org.jetbrains.kotlin.gradle.newTests.*

object AndroidGradlePluginVersionTestsProperty : KotlinTestsResolvableProperty {
    override val id: String = "AGP_VERSION"

    enum class Value(val acronym: String, val version: String) {
        MinSupported("MIN", "7.2.1"),
        LatestStable("STABLE", "7.4.2"),
        Beta("BETA", "8.0.0-beta03"),
        Alpha("ALPHA", "8.1.0-alpha05")
    }

    override val valuesByAcronyms: Map<String, String> = Value.values().map { it.acronym to it.version }.toMap()

    override val defaultValue: String = Value.LatestStable.version
}
