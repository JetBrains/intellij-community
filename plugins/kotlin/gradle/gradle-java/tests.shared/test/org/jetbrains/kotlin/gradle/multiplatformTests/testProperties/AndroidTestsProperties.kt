// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testProperties

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinTestsResolvableProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.ValueFromEnvironment

object AndroidGradlePluginVersionTestsProperty : KotlinTestsResolvableProperty<AndroidGradlePluginVersionTestsProperty.Value> {
    override val id: String = "AGP_VERSION"

    enum class Value(override val versionAlias: String, override val version: String) : ValueFromEnvironment {
        MinSupported("MIN", "7.4.2"),
        LatestStable("STABLE", "8.3.1"),
        Beta("BETA", "8.5.0-beta01"),
        Alpha("ALPHA", "8.6.0-alpha03");
    }

    override val versionByAlias: Map<Value, String> = Value.entries.associate { it to it.version }

    override val defaultValue: Value = Value.LatestStable
}
