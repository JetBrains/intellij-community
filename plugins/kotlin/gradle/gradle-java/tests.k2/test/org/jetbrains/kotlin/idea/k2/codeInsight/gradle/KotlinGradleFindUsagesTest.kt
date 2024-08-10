// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KMP_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.sorted
import kotlin.test.assertTrue

/**
 * @see org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.KotlinGradleTomlVersionCatalogReferencesSearcher
 */
@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/findUsages/")
class KotlinGradleFindUsagesTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlVersionUsageInBuildGradleKts.test")
    fun testTomlVersionUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlLibraryUsageInBuildGradleKts.test")
    fun testTomlLibraryUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlLibraryUsageInTomlAndBuildGradleKts.test")
    fun testTomlLibraryUsageInTomlAndBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("tomlPluginLibraryUsageBuildGradleKts.test")
    fun testTomlPluginLibraryUsageBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("topLevelPropertyNoUsage.test")
    fun testTopLevelPropertyNoUsage(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("topLevelPropertyUsageInBuildGradleKts.test")
    fun testTopLevelPropertyUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("topLevelFunctionUsageInBuildGradleKts.test")
    fun testTopLevelFunctionUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("classUsageInBuildGradleKts.test")
    fun testClassUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("propertyByProjectUsageInBuildGradleKts.test")
    fun testPropertyByProjectUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("propertyBySettingsUsageInSettingsGradleKts.test")
    fun testPropertyBySettingsUsageInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("propertyByExtraUsageInBuildGradleKts.test")
    fun testPropertyByExtraUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("classFromBuildSrcUsageInBuildGradleKts.test")
    fun testClassFromBuildSrcUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }

    private fun verifyFindUsages(gradleVersion: GradleVersion) {
        test(gradleVersion, GRADLE_KMP_KOTLIN_FIXTURE) {
            val mainFileContent = mainTestDataFile
            var mainFile = mainTestDataPsiFile
            val noExpectedFindUsages =
                InTextDirectivesUtils.isDirectiveDefined(mainFileContent.content, "// \"NO-EXPECTED-FIND_USAGE\"")
            val expectedFindUsageFileAndText =
                InTextDirectivesUtils.findListWithPrefixes(mainFileContent.content, "// \"EXPECTED-FIND_USAGE-FILE_TEXT\": ")

            codeInsightFixture.configureFromExistingVirtualFile(mainFile.virtualFile)
            assertTrue("<caret> is not present") {
                val caretOffset = runReadAction { codeInsightFixture.caretOffset }
                caretOffset != 0
            }
            runInEdtAndWait {
                val elementAtCaret = fixture.elementAtCaret
                val usagesPsi = ReferencesSearch.search(elementAtCaret).findAll()
                val usages = usagesPsi.map { "${it.element.containingFile.name} ${it.element.text}" }

                if (usages.isNotEmpty()) {
                    assertEquals(expectedFindUsageFileAndText, usages.sorted())
                } else {
                    assertTrue(noExpectedFindUsages, "no expected find usages, but theses were found: $usages")
                }
            }
        }
    }
}
