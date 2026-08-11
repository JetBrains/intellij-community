// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

@TestRoot("idea/tests/testData/")
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation/")
abstract class KotlinGradleGotoDeclarationTest : AbstractKotlinGradleNavigationTest() {
    override fun setUp() {
        Registry.get("kotlin.scripting.index.dependencies.sources").setValue(true)
        super.setUp()
    }

    override fun tearDown() = try {
        super.tearDown()
    } finally {
        Registry.get("kotlin.scripting.index.dependencies.sources").resetToDefault()
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("projectDependency.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testProjectDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("sdkDependencySources.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSdkDependencySources(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("projectKmpDependency.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testProjectKmpDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("librarySourceDependency.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testLibrarySourceDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("pluginPrecompiled/inGroovy.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginPrecompiledInGroovy(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("pluginPrecompiled/inKotlin.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginPrecompiledInKotlin(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("pluginPrecompiled/inKotlinWithPackage.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginPrecompiledInKotlinWithPackage(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TestMetadata("pluginPrecompiled/inKotlinLocatedInJavaDir.test")
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginPrecompiledInKotlinLocatedInJavaDir(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    override val myFixture = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
        withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
            setProjectName("GradleKotlinFixture")
            include("module1")
        }
        withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
            withKotlinDsl()
            withMavenCentral()
        }
        withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
            withPlugin { code("java") }
        }
    }
}