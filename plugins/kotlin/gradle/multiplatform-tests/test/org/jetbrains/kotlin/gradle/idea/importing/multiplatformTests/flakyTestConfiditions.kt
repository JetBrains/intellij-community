// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.TestWithKotlinPluginAndGradleVersions
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions

// KT-82895: KGP import is flaky before 2.2.0
fun TestWithKotlinPluginAndGradleVersions.flakyKgpImportKT82895() =
    kotlinPluginVersion.version > KotlinGradlePluginVersions.V_2_1_0 &&
        kotlinPluginVersion.version < KotlinGradlePluginVersions.V_2_2_0