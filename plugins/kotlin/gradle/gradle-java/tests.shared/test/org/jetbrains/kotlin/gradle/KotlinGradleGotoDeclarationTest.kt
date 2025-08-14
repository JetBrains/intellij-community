// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation/")
abstract class KotlinGradleGotoDeclarationTest : AbstractKotlinGradleNavigationTest() {
    override fun setUp() {
        Registry.get("kotlin.scripting.index.dependencies.sources").setValue(true)
        super.setUp()
    }

    override fun tearDown() {
        Registry.get("kotlin.scripting.index.dependencies.sources").resetToDefault()
        super.tearDown()
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectDependency.test")
    fun testProjectDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectKmpDependency.test")
    fun testProjectKmpDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectAccessorSimpleModule.test")
    fun testProjectAccessorSimpleModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectAccessorSubSubModule.test")
    fun testProjectAccessorSubSubModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectFullAccessorSubSubModule.test")
    fun testProjectFullAccessorSubSubModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectAccessorSubModuleInTheMiddle.test")
    fun testProjectAccessorSubModuleInTheMiddle(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("librarySourceDependency.test")
    fun testLibrarySourceDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inGroovy.test")
    fun testPluginPrecompiledInGroovy(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inKotlin.test")
    fun testPluginPrecompiledInKotlin(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inKotlinWithPackage.test")
    fun testPluginPrecompiledInKotlinWithPackage(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inKotlinLocatedInJavaDir.test")
    fun testPluginPrecompiledInKotlinLocatedInJavaDir(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    override val myFixture = GRADLE_KMP_KOTLIN_FIXTURE

    companion object {
        val GRADLE_KMP_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("GradleKotlinFixture")
                include("module1", ":module1:a-module11", ":module1:a-module11:module111")
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "buildSrc", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
            }
            withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "module1/a-module11", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "module1/a-module11/module111", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withFile(
                "gradle/libs.versions.toml",
                /* language=TOML */
                """
                [libraries]
                some_test-library = { module = "org.junit.jupiter:junit-jupiter" }
                [plugins]
                kotlin = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin"}
                [versions]
                test_library-version = "1.0"
                kotlin = "1.9.24"
                """.trimIndent()
            )
            withFile(
                "gradle.properties",
                """
                kotlin.code.style=official
                """.trimIndent()
            )
            withFile(
                "buildSrc/src/main/kotlin/MyTask.kt",
                """
                    
                """.trimIndent()
            )
            withDirectory("src/main/kotlin")
        }

        val GRADLE_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("GradleKotlinFixture")
                include(":module1")
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
                withMavenCentral()
            }
            withFile(
                "gradle.properties",
                """
                kotlin.code.style=official
                """.trimIndent()
            )
        }
    }
}