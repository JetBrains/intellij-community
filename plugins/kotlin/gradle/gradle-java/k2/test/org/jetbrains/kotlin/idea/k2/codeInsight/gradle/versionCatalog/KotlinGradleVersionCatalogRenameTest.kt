// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.versionCatalog

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
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
            val fileToCheckPath = getDirectiveValue(mainFile, "## FILE_TO_CHECK: ")
            codeInsightFixture.configureFromExistingVirtualFile(mainFile)
            runInEdtAndWait {
                TestDialogManager.setTestDialog(TestDialog.OK)
                codeInsightFixture.renameElementAtCaret(newName)

                codeInsightFixture.openFileInEditor(getFile(fileToCheckPath))
                val expectedResult = getFile("$fileToCheckPath.after").readText()
                codeInsightFixture.checkResult(expectedResult, true)
                // TODO: check navigation from the renamed reference to is declaration in `mainFile` - when IDEA-302835 is implemented
                // There is a problem with resolving renamed reference in build script to the corresponding element in a version catalog.
                // The resolving relies on accessor classes that Gradle generates in .gradle/{Gradle version}/dependencies-accessors/{hash}.
                // The accessors are generated when Gradle sync is executed which does not happen while renaming in version catalog.
                // Hence, some parts of renamed references are unresolved and navigation from them is not possible.
            }
        }
    }

    private fun getDirectiveValue(mainFile: VirtualFile, directiveKey: String): String {
        val directiveValue = InTextDirectivesUtils.findStringWithPrefixes(mainFile.readText(), directiveKey)
        return assertNotNull(directiveValue, "'$directiveKey' directive is not found in the test file")
    }
}
