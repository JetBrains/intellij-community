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
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation/")
abstract class KotlinGradleGotoDependenciesSourcesTest : AbstractKotlinGradleNavigationTest() {
    override fun setUp() {
        super.setUp()
        Registry.get("kotlin.scripting.index.dependencies.sources").setValue(true)
    }

    override fun tearDown() {
        Registry.get("kotlin.scripting.index.dependencies.sources").resetToDefault()
        super.tearDown()
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("librarySourceDependency2.test")
    fun testLibrarySourceDependency2(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    override val myFixture = GRADLE_KOTLIN_FIXTURE

    companion object {
        val GRADLE_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("GradleKotlinFixture")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
                withMavenCentral()
                withKotlinTest()
            }
        }
    }
}