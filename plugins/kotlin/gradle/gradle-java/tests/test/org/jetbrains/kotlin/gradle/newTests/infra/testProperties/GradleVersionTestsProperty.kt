// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.infra.testProperties

import org.jetbrains.kotlin.gradle.newTests.KotlinTestsResolvableProperty

object GradleVersionTestsProperty : KotlinTestsResolvableProperty {
    override val id: String = "gradle_version"

    enum class Value(val acronym: String, val version: String) {
        ForMinAgp("REQUIRED_FOR_MIN_AGP", "7.4"),
        ForStableAgp("REQUIRED_FOR_STABLE_AGP", "7.5.1"),
        ForBetaAgp("REQUIRED_FOR_BETA_AGP", "8.0-rc-1"),
        ForAlphaAgp("REQUIRED_FOR_ALPHA_AGP", "8.0-rc-1")
    }

    override val valuesByAcronyms: Map<String, String> = Value.values().map { it.acronym to it.version }.toMap()

    override val defaultValue: String = Value.ForStableAgp.version
}
