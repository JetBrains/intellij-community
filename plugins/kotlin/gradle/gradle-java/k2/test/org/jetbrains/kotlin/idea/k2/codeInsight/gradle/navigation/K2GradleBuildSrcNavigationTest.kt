// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.navigation

import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest
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
@TestMetadata("../../../idea/tests/testData/gradle/navigation/buildSrc")
class K2GradleBuildSrcNavigationTest : AbstractKotlinGradleNavigationTest() {
    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(BUILD_SRC_AS_INCLUDED_BUILD_SUPPORTED_VERSIONS)
    @TestMetadata("functionFromRootBuildGradleKts.test")
    fun testFunctionFromRootBuildGradleKts(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(BUILD_SRC_AS_INCLUDED_BUILD_SUPPORTED_VERSIONS)
    @TestMetadata("constantFromSubprojectBuildGradleKts.test")
    fun testConstantFromSubprojectBuildGradleKts(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(BUILD_SRC_AS_INCLUDED_BUILD_SUPPORTED_VERSIONS)
    @TestMetadata("constantFromPluginsBlock.test")
    fun testConstantFromPluginsBlock(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    override val myFixture = GRADLE_BUILD_SRC_FIXTURE
}
