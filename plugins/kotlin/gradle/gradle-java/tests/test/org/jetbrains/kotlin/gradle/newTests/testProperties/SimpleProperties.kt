// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package org.jetbrains.kotlin.gradle.newTests.testProperties

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

fun SimpleProperties(gradleVersion: GradleVersion, kgpVersion: KotlinToolingVersion) : Map<String, String> = mapOf(
    "kts_kotlin_plugin_repositories" to GradleKotlinTestUtils.listRepositories(useKts = true, gradleVersion, kgpVersion),
    "kotlin_plugin_repositories" to GradleKotlinTestUtils.listRepositories(useKts = false, gradleVersion, kgpVersion),

    "compile_sdk_version" to "31",
    "build_tools_version" to "28.0.3"
)
