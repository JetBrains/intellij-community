// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.versionCatalog

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.k2.codeInsight.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith

/**
 * @see org.jetbrains.kotlin.idea.gradleCodeInsightCommon.versionCatalog.KotlinGradleVersionCatalogReferencesSearcher
 */
@TestRoot("gradle/gradle-java/tests.k2")
@RunWith(JUnit3RunnerWithInners::class)
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/versionCatalog/findUsages")
class KotlinGradleVersionCatalogFindUsagesTest : AbstractGradleCodeInsightTest() {

    @TestMetadata("tomlVersionUsageInBuildGradleKts.test")
    fun testTomlVersionUsageInBuildGradleKts() {
        testVersionCatalogFindUsages { usages ->
            assertEquals(1, usages.size)
            assertContainsUsage(usages, "build.gradle.kts", "libs.versions.aaa.bbb.ccc")
        }
    }

    @TestMetadata("tomlLibraryUsageInBuildGradleKts.test")
    fun testTomlLibraryUsageInBuildGradleKts() {
        testVersionCatalogFindUsages { usages ->
            assertEquals(1, usages.size)
            assertContainsUsage(usages, "build.gradle.kts", "libs.aaa.bbb.ccc")
        }
    }

    @TestMetadata("tomlLibraryUsageInTomlAndBuildGradleKts.test")
    fun testTomlLibraryUsageInTomlAndBuildGradleKts() {
        testVersionCatalogFindUsages { usages ->
            assertEquals(2, usages.size)
            assertContainsUsage(usages, "libs.versions.toml", "\"aaa_bbb-ccc\"")
            assertContainsUsage(usages, "build.gradle.kts", "libs.aaa.bbb.ccc")
        }
    }

    private fun testVersionCatalogFindUsages(checker: (Collection<PsiReference>) -> Unit) {
        runInEdtAndWait {
            val elementAtCaret = fixture.elementAtCaret
            assertNotNull("Element at caret not found", elementAtCaret)
            val usages = ReferencesSearch.search(elementAtCaret).findAll()
            checker(usages)
        }
    }

    private fun assertContainsUsage(foundUsages: Collection<PsiReference>, expectedUsageFileName: String, expectedUsageText: String) {
        val tomlUsage = foundUsages.find {
            it.element.containingFile.name == expectedUsageFileName && it.element.text == expectedUsageText
        }
        assertNotNull("Expected usage '$expectedUsageText' in '$expectedUsageFileName' was not found", tomlUsage)
    }
}
