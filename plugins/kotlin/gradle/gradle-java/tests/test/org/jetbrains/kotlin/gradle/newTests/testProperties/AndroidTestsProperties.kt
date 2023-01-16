// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testProperties

import org.jetbrains.kotlin.gradle.newTests.*

object AndroidGradlePluginVersionTestsProperty : KotlinTestsResolvableProperty {
    override val id: String = "android_gradle_plugin_version"

    enum class Values(val acronym: String, val version: String) {
        MinSupported("MIN", "7.2"),
        LatestStable("STABLE", "7.4.0"),
        Beta("BETA", "8.0.0-beta01"),
        Alpha("ALPHA", "8.1.0-alpha01")
    }

    override val valuesByAcronyms: Map<String, String> = Values.values().map { it.acronym to it.version }.toMap()

    override val defaultValue: String = Values.LatestStable.version
}
