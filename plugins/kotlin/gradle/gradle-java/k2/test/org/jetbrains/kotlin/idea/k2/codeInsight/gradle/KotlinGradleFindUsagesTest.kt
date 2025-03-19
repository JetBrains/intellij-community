// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
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
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertTrue

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/findUsages/")
class KotlinGradleFindUsagesTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/tomlVersionUsageInBuildGradleKts.test")
    fun testTomlVersionUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/tomlLibraryUsageInBuildGradleKts.test")
    fun testTomlLibraryUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/tomlLibraryUsageInTomlAndBuildGradleKts.test")
    fun testTomlLibraryUsageInTomlAndBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/tomlPluginLibraryUsageBuildGradleKts.test")
    fun testTomlPluginLibraryUsageBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/includedBuild/tomlVersionUsageInBuildGradleKts.test")
    fun testIncludedBuildTomlVersionUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/includedBuild/tomlLibraryUsageInBuildGradleKts.test")
    fun testIncludedBuildTomlLibraryUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/includedBuild/tomlLibraryUsageInTomlAndBuildGradleKts.test")
    fun testIncludedBuildTomlLibraryUsageInTomlAndBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/includedBuild/tomlPluginLibraryUsageBuildGradleKts.test")
    fun testIncludedBuildTomlPluginLibraryUsageBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalog/includedBuildWithoutSettings/tomlLibraryUsageInBuildGradleKts.test")
    fun testIncludedBuildWithoutSettingsTomlLibraryUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
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
    @Disabled("TODO: find usage from gradle plugin source module works, test should be reviewed end enabled")
    fun testClassFromBuildSrcUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion)
    }
}

fun AbstractGradleCodeInsightTest.verifyFindUsages(gradleVersion: GradleVersion, builder: GradleTestFixtureBuilder = GRADLE_KMP_KOTLIN_FIXTURE) {
    test(gradleVersion, builder) {
        val mainFileContent = mainTestDataFile
        val mainFile = mainTestDataPsiFile
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
            val projectDir = project.guessProjectDir() ?: error("project dir is not found")
            val usages = usagesPsi.map {
                val virtualFile = it.element.containingFile.virtualFile
                val fileRelativePath = VfsUtilCore.getRelativePath(virtualFile, projectDir)
                return@map "$fileRelativePath ${it.element.text}"
            }

            if (noExpectedFindUsages) {
                assertTrue(usages.isEmpty(), "no expected find usages, but theses were found: $usages")
            } else {
                assertEquals(expectedFindUsageFileAndText, usages.sorted())
            }
        }
    }
}
