// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.TYPESAFE_PROJECT_ACCESSORS_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

@TestRoot("idea/tests/testData/")
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation/")
abstract class KotlinGradleGotoProjectAccessorTest : AbstractKotlinGradleNavigationTest() {
    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("projectAccessorSimpleModule.test")
    @TargetVersions(TYPESAFE_PROJECT_ACCESSORS_SUPPORTED_VERSIONS)
    fun testProjectAccessorSimpleModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("projectAccessorSubSubModule.test")
    @TargetVersions(TYPESAFE_PROJECT_ACCESSORS_SUPPORTED_VERSIONS)
    fun testProjectAccessorSubSubModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("projectFullAccessorSubSubModule.test")
    @TargetVersions(TYPESAFE_PROJECT_ACCESSORS_SUPPORTED_VERSIONS)
    fun testProjectFullAccessorSubSubModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("projectAccessorSubModuleInTheMiddle.test")
    @TargetVersions(TYPESAFE_PROJECT_ACCESSORS_SUPPORTED_VERSIONS)
    fun testProjectAccessorSubModuleInTheMiddle(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    override val myFixture = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
        withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
            setProjectName("GradleKotlinFixture")
            include("module1", ":module1:a-module11", ":module1:a-module11:module111")
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
        }
        withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
            withKotlinDsl()
            withMavenCentral()
        }
        withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
            withPlugin { code("java") }
        }
        withBuildFile(gradleVersion, "module1/a-module11", gradleDsl = GradleDsl.KOTLIN) {
            withPlugin { code("java") }
        }
        withBuildFile(gradleVersion, "module1/a-module11/module111", gradleDsl = GradleDsl.KOTLIN) {
            withPlugin { code("java") }
        }
    }

}