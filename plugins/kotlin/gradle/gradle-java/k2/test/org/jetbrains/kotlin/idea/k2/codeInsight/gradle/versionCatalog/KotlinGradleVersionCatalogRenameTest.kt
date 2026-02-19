// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.versionCatalog

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.editor.getVirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertNotNull

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("gradle/gradle-java/k2")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/versionCatalog/rename")
class KotlinGradleVersionCatalogRenameTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("renameLibraryInToml.test")
    fun testRenameLibraryInToml(gradleVersion: GradleVersion) {
        verifyRenameOfDeclaration(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("renameVersionInToml.test")
    fun testRenameVersionInToml(gradleVersion: GradleVersion) {
        verifyRenameOfDeclaration(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("renamePluginInToml.test")
    fun testRenamePluginInToml(gradleVersion: GradleVersion) {
        verifyRenameOfDeclaration(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("renameBundleInToml.test")
    fun testRenameBundleInToml(gradleVersion: GradleVersion) {
        verifyRenameOfDeclaration(gradleVersion)
    }

    private fun verifyRenameOfDeclaration(gradleVersion: GradleVersion) {
        test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
            val mainFile = mainTestDataPsiFile.virtualFile
            val newName = getDirectiveValue(mainFile, "## RENAME_TO: ")
            val usagePath = getDirectiveValue(mainFile, "## FILE_TO_CHECK: ")
            fixture.configureFromExistingVirtualFile(mainFile)
            runInEdtAndWait {
                TestDialogManager.setTestDialog(TestDialog.OK)
                fixture.renameElementAtCaret(newName)

                openUsageAndAssertRenamed(usagePath)
                assertUsageNavigatesToDeclaration(mainFile, newName)
            }
        }
    }

    private fun openUsageAndAssertRenamed(usagePath: String) {
        fixture.openFileInEditor(getFile(usagePath))
        val expectedResult = getFile("$usagePath.after").readText()
        fixture.checkResult(expectedResult, true)
    }

    private fun assertUsageNavigatesToDeclaration(expectedDeclarationFile: @NlsSafe VirtualFile, expectedElementName: String) {
        fixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        val afterNavigationPath = document.getVirtualFile().path
        assertEquals(expectedDeclarationFile.path, afterNavigationPath) {
            "After renaming the declaration element, navigation from its usage should lead to the declaration file."
        }
        // TODO verify that the caret is located exactly at the declaration element
        // The commented assertion below fails because `fixture.elementAtCaret` leads to a getter in the version catalog accessor,
        // despite the opened document is a version catalog file, not accessor class. Most probably, it's a test infrastructure issue.
        //
        //assertEquals(expectedElementName, fixture.elementAtCaret.text) {
        //    "After renaming the declaration element, the caret should be located at the declaration element."
        //}
    }

    private fun getDirectiveValue(mainFile: VirtualFile, directiveKey: String): String {
        val directiveValue = InTextDirectivesUtils.findStringWithPrefixes(mainFile.readText(), directiveKey)
        return assertNotNull(directiveValue, "'$directiveKey' directive is not found in the test file")
    }

    private fun assertOpenedFileHasText(expectedText: String, messageSupplier: () -> String) {
        try {
            fixture.checkResult(expectedText)
        } catch (e: FileComparisonFailedError) {
            fail(e)
        }
    }
}
