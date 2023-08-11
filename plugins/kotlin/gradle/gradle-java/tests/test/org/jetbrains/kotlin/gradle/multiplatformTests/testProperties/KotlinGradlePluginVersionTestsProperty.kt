// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testProperties

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinTestsResolvableProperty
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions

// TODO: deduplicate with [org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions]
//       when the new tests infra becomes enabled by default
object KotlinGradlePluginVersionTestsProperty : KotlinTestsResolvableProperty {
    override val id: String = "KGP_VERSION"

    @Suppress("unused") // passed using environment variables
    enum class Value(val acronym: String, val version: String) {
        MinSupported("MIN", "1.7.21"),
        PreviousMajorRelease("PREV_RELEASE", "1.8.22"),
        LatestStable("STABLE", "1.9.0"),
        Latest("LATEST", KotlinGradlePluginVersions.latest.toString()),
        SNAPSHOT("SNAPSHOT", KotlinGradlePluginVersions.latest.run { "$major.$minor.255-SNAPSHOT" })
    }

    override val valuesByAcronyms: Map<String, String> = Value.values().associate { it.acronym to it.version }

    override val defaultValue: String = Value.Latest.version
}
