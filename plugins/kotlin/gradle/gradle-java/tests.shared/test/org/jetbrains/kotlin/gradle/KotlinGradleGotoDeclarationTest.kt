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

    override fun tearDown() = try {
        super.tearDown()
    } finally {
        Registry.get("kotlin.scripting.index.dependencies.sources").resetToDefault()
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectDependency.test")
    fun testProjectDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }


    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("sdkDependencySources.test")
    fun testSdkDependencySources(gradleVersion: GradleVersion) {
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
}