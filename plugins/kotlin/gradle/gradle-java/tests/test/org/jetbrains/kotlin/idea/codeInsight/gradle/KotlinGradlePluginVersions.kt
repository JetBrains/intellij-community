// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isStable

object KotlinGradlePluginVersions {
    val V_1_7_21 = KotlinToolingVersion(1, 7, 21, null)
    val V_1_8_0 = KotlinToolingVersion(1, 8, 0, null)
    val latest = KotlinToolingVersion("1.9.0-dev-764")

    val all = listOf(
        V_1_7_21,
        V_1_8_0,
        latest
    )

    val allStable = all.filter { it.isStable }

    val lastStable = allStable.max()
}
