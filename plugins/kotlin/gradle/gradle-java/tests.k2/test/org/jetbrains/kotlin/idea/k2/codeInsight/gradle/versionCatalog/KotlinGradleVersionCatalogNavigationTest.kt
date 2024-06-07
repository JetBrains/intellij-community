// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.versionCatalog

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.gradle.AbstractGradleFullSyncCodeInsightTest
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith

/**
 * @see org.jetbrains.kotlin.idea.gradleCodeInsightCommon.versionCatalog.KotlinGradleVersionCatalogGotoDeclarationHandler
 */
@TestRoot("gradle/gradle-java/tests.k2")
@RunWith(JUnit3RunnerWithInners::class)
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/versionCatalog/navigation")
class KotlinGradleVersionCatalogNavigationTest : AbstractGradleFullSyncCodeInsightTest() {

    @TestMetadata("fromVersionUsageToItsDeclarationInToml.test")
    fun testVersionCatalogFromVersionUsageToItsDeclarationInToml() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("fromLibraryUsageToItsDeclarationInToml.test")
    fun testVersionCatalogFromLibraryUsageToItsDeclarationInToml() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("fromLibraryUsageWithGetToItsDeclarationInToml.test")
    fun testVersionCatalogFromLibraryUsageWithGetToItsDeclarationInToml() {
        verifyNavigationFromCaretToExpected()
    }

    private fun verifyNavigationFromCaretToExpected() {
        fixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        val text = document.text
        assertTrue("Actual text:\n\n$text", text.startsWith("# EXPECTED"))
    }
}
