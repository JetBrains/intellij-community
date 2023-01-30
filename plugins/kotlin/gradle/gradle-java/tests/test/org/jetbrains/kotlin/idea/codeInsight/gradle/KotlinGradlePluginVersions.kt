// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isStable

object KotlinGradlePluginVersions {
    val V_1_4_32 = KotlinToolingVersion(1, 4, 32, null)
    val V_1_5_32 = KotlinToolingVersion(1, 5, 32, null)
    val V_1_6_21 = KotlinToolingVersion(1, 6, 21, null)
    val V_1_7_20 = KotlinToolingVersion(1, 7, 20, null)
    val latest = KotlinToolingVersion("1.9.0-dev-764")

    val all = listOf(
        V_1_4_32,
        V_1_5_32,
        V_1_6_21,
        V_1_7_20,
        latest
    )

    val allStable = all.filter { it.isStable }

    val lastStable = allStable.max()
}
