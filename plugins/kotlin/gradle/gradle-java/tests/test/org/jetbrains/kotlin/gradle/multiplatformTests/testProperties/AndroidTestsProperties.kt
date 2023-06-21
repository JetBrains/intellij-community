// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testProperties

import org.jetbrains.kotlin.gradle.multiplatformTests.*

object AndroidGradlePluginVersionTestsProperty : KotlinTestsResolvableProperty {
    override val id: String = "AGP_VERSION"

    enum class Value(val acronym: String, val version: String) {
        MinSupported("MIN", "7.4.2"),
        LatestStable("STABLE", "8.0.2"),
        Beta("BETA", "8.1.0-beta05"),
        Alpha("ALPHA", "8.2.0-alpha08")
    }

    override val valuesByAcronyms: Map<String, String> = Value.values().map { it.acronym to it.version }.toMap()

    override val defaultValue: String = Value.LatestStable.version
}
