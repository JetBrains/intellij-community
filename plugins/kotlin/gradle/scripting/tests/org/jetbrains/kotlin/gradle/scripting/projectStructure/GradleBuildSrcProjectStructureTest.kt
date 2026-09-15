// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.projectStructure

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.GRADLE_BUILD_SRC_FIXTURE
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.junit.jupiter.params.ParameterizedTest

@TestMetadata("testData/gradleScriptBuildSrc")
internal class GradleBuildSrcProjectStructureTest : AbstractGradleKotlinProjectStructureTest() {
    @ParameterizedTest
    @GradleTestSource("8.11")
    @TestMetadata("buildSrc.test")
    fun testBuildSrc(gradleVersion: GradleVersion) {
        checkProjectStructure(gradleVersion, GRADLE_BUILD_SRC_FIXTURE)
    }
}
