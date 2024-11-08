// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.versionCatalog

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("gradle/gradle-java/tests.k2")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/versionCatalog/navigation")
class KotlinGradleVersionCatalogNavigationTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("fromVersionUsageToItsDeclarationInToml.test")
    fun testFromVersionUsageToItsDeclarationInToml(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("fromLibraryUsageToItsDeclarationInToml.test")
    fun testFromLibraryUsageToItsDeclarationInToml(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("fromLibraryUsageWithGetToItsDeclarationInToml.test")
    fun testFromLibraryUsageWithGetToItsDeclarationInToml(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("includedBuild/fromVersionUsageToItsDeclarationInToml.test")
    fun testIncludedBuildFromVersionUsageToToml(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("includedBuild/fromLibraryUsageToItsDeclarationInToml.test")
    fun testIncludedBuildFromLibraryUsageToToml(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("includedBuild/fromLibraryUsageWithGetToItsDeclarationInToml.test")
    fun testIncludedBuildFromLibraryWithGetUsageToToml(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("includedBuildWithoutSettings/fromLibraryUsageToItsDeclarationInToml.test")
    fun testIncludedBuildWithoutSettingsFromLibraryUsageToToml(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    private fun verifyNavigationFromCaretToExpected(gradleVersion: GradleVersion) {
        test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
            codeInsightFixture.configureFromExistingVirtualFile(mainTestDataPsiFile.virtualFile)
            fixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
            val text = document.text
            assertTrue(text.startsWith("# EXPECTED"), "Actual text:\n\n$text")
        }
    }
}
