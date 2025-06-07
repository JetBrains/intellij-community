// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

object KotlinGradlePluginVersions {
    val V_1_7_21 = KotlinToolingVersion(1, 7, 21, null)
    val V_1_8_22 = KotlinToolingVersion(1, 8, 22, null)
    val V_2_1_0 = KotlinToolingVersion(2, 1, 0, null)
    val latest = KotlinToolingVersion("2.2.0-dev-15683")
    val latestBootstrap = KotlinToolingVersion("1.9.20-dev-6845")
    val latestStable = V_1_8_22
}
