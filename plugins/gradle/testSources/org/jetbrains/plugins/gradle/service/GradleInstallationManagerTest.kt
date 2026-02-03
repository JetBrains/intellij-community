// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.junit.Test

class GradleInstallationManagerTest : GradleInstallationManagerTestCase() {
  @Test
  fun testGradleVersionResolving() {
    val gradle_4_10 = GradleVersion.version("4.10")
    val gradle_6_0 = GradleVersion.version("6.0")
    val currentGradle = GradleVersion.current()

    doTestGradleVersion(gradle_4_10, DistributionType.LOCAL, wrapperVersionToGenerate = gradle_4_10)
    doTestGradleVersion(gradle_6_0, DistributionType.LOCAL, wrapperVersionToGenerate = gradle_6_0)
    doTestGradleVersion(currentGradle, DistributionType.BUNDLED, wrapperVersionToGenerate = null)
    doTestGradleVersion(currentGradle, DistributionType.DEFAULT_WRAPPED, wrapperVersionToGenerate = null)
    doTestGradleVersion(gradle_4_10, DistributionType.DEFAULT_WRAPPED, wrapperVersionToGenerate = gradle_4_10)
    doTestGradleVersion(gradle_6_0, DistributionType.DEFAULT_WRAPPED, wrapperVersionToGenerate = gradle_6_0)
  }
}