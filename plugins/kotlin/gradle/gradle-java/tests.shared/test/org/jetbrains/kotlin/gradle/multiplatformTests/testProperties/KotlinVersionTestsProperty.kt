// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testProperties

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinTestsResolvableProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.ValueFromEnvironment
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions

// TODO: deduplicate with [org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions]
//       when the new tests infra becomes enabled by default
object KotlinVersionTestsProperty : KotlinTestsResolvableProperty<KotlinVersionTestsProperty.Value> {
    override val id: String = "KGP_VERSION"

    @Suppress("unused") // passed using environment variables
    enum class Value(override val versionAlias: String, override val version: String) : ValueFromEnvironment {
        MinSupported("MIN", "1.7.21"),
        PreviousMajorRelease("PREV_RELEASE", "1.8.22"),
        LatestStable("STABLE", KotlinGradlePluginVersions.V_2_3_0.toString()),
        Latest("LATEST", KotlinGradlePluginVersions.latest.toString()),
        SNAPSHOT("SNAPSHOT", KotlinGradlePluginVersions.latest.run { "$major.$minor.255-SNAPSHOT" })
    }

    override val versionByAlias: Map<Value, String> = Value.entries.associateWith { it.version }

    override val defaultValue: Value = Value.Latest
}
