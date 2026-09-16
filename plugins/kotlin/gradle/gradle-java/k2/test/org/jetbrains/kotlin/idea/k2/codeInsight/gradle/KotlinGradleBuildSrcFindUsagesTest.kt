// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.GRADLE_BUILD_SRC_FIXTURE
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.BUILD_SRC_AS_INCLUDED_BUILD_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

@GradleProjectTestApplication
@TestRoot("idea/tests/testData/")
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/findUsages/buildSrc")
class KotlinGradleBuildSrcFindUsagesTest : AbstractGradleCodeInsightTest() {
    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("functionUsageInBuildGradleKts.test")
    @TargetVersions(BUILD_SRC_AS_INCLUDED_BUILD_SUPPORTED_VERSIONS)
    fun testFunctionUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_BUILD_SRC_FIXTURE)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("constantUsageInSubprojectBuildGradleKts.test")
    @TargetVersions(BUILD_SRC_AS_INCLUDED_BUILD_SUPPORTED_VERSIONS)
    fun testConstantUsageInSubprojectBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_BUILD_SRC_FIXTURE)
    }
}
