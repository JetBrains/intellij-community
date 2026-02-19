// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

interface TestWithKotlinPluginAndGradleVersions {
    val testGradleVersion: TestVersion<GradleVersion>
    val kotlinPluginVersion: TestVersion<KotlinToolingVersion>
}

interface TestWithAndroidVersion {
    val agpVersion: TestVersion<String>?
}