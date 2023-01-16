// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testProperties

import org.jetbrains.kotlin.gradle.newTests.KotlinTestsResolvableProperty
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions

// TODO: deduplicate with [org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions]
//       when the new tests infra becomes enabled by default
object KotlinGradlePluginVersionTestsProperty : KotlinTestsResolvableProperty {
    override val id: String = "kotlin_plugin_version"

    enum class Values(val acronym: String, val version: String) {
        PreviousMajorRelease("PREV_RELEASE", "1.7.10"),
        LatestStable("STABLE", "1.7.20"),
        NextRelease("NEXT_RELEASE", "1.8.0"),
        Latest("LATEST", KotlinGradlePluginVersions.latest.toString())
    }

    override val valuesByAcronyms: Map<String, String> = Values.values().associate { it.acronym to it.version }

    override val defaultValue: String = Values.Latest.version
}
