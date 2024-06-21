// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.versionCatalog

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest

/**
 * @see org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.KotlinGradleTomlVersionCatalogReferencesSearcher
 */
@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("gradle/gradle-java/tests.k2")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/versionCatalog/findUsages")
class KotlinGradleVersionCatalogFindUsagesTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlVersionUsageInBuildGradleKts.test")
    fun testTomlVersionUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        testVersionCatalogFindUsages(gradleVersion) { usages ->
            assertEquals(1, usages.size)
            assertContainsUsage(usages, "build.gradle.kts", "libs.versions.test.library.version")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlLibraryUsageInBuildGradleKts.test")
    fun testTomlLibraryUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        testVersionCatalogFindUsages(gradleVersion) { usages ->
            assertEquals(1, usages.size)
            assertContainsUsage(usages, "build.gradle.kts", "libs.some.test.library")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlLibraryUsageInTomlAndBuildGradleKts.test")
    fun testTomlLibraryUsageInTomlAndBuildGradleKts(gradleVersion: GradleVersion) {
        testVersionCatalogFindUsages(gradleVersion) { usages ->
            assertEquals(2, usages.size)
            assertContainsUsage(usages, "libs.versions.toml", "\"some_test-library\"")
            assertContainsUsage(usages, "build.gradle.kts", "libs.some.test.library")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlPluginLibraryUsageBuildGradleKts.test")
    fun testTomlPluginLibraryUsageBuildGradleKts(gradleVersion: GradleVersion) {
        testVersionCatalogFindUsages(gradleVersion) { usages ->
            assertEquals(1, usages.size)
            assertContainsUsage(usages, "build.gradle.kts", "libs.plugins.kotlin")
        }
    }

    private fun testVersionCatalogFindUsages(gradleVersion: GradleVersion, checker: (Collection<PsiReference>) -> Unit) {
        test(gradleVersion, GRADLE_KOTLIN_FIXTURE) {
            codeInsightFixture.configureFromExistingVirtualFile(mainTestDataPsiFile.virtualFile)
            runInEdtAndWait {
                val elementAtCaret = fixture.elementAtCaret
                assertNotNull(elementAtCaret, "Element at caret not found")
                val usages = ReferencesSearch.search(elementAtCaret).findAll()
                checker(usages)
            }
        }
    }

    private fun assertContainsUsage(foundUsages: Collection<PsiReference>, expectedUsageFileName: String, expectedUsageText: String) {
        val tomlUsage = foundUsages.find {
            it.element.containingFile.name == expectedUsageFileName && it.element.text == expectedUsageText
        }
        assertNotNull(tomlUsage, "Expected usage '$expectedUsageText' in '$expectedUsageFileName' was not found")
    }

}
