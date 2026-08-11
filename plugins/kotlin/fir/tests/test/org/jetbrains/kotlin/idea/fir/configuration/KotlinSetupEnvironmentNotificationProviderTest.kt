// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.configuration

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.configuration.KotlinSetupEnvironmentNotificationProvider
import org.junit.Test
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinSetupEnvironmentNotificationProviderTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testFileUnderSrcMainKotlin() {
        val kotlinFileRelativePath = "/main/kotlin/TestKotlin.kt"
        createKotlinFile(kotlinFileRelativePath)
        assertFileIsUnderSourceRoots(kotlinFileRelativePath, fileShouldBeUnderSourceRoots = true)
    }

    @Test
    fun testFileUnderSrcTestKotlin() {
        val kotlinFileRelativePath = "/test/kotlin/TestKotlin.kt"
        createKotlinFile(kotlinFileRelativePath)
        assertFileIsUnderSourceRoots(kotlinFileRelativePath, fileShouldBeUnderSourceRoots = true)
    }

    @Test
    fun testFileNotUnderSrcMainKotlin() {
        val kotlinFileRelativePath = "/main/kotlin_smth.kt"
        createKotlinFile(kotlinFileRelativePath)
        assertFileIsUnderSourceRoots(kotlinFileRelativePath, fileShouldBeUnderSourceRoots = false)
    }

    /**
     * Tests the edge case where a Kotlin file is under `src/main/kotlin/` structure
     * but the content root is set to `main`, making its parent `projectParent.name == "src"`.
     *
     * **Directory structure:**
     * ```
     * app
     * └── src/                    ← projectParent (parent of content root)
     *     └── main/               ← content root (set by this test)
     *         └── kotlin/
     *             └── App.kt
     * ```
     *
     * This is a specific scenario that the [org.jetbrains.kotlin.idea.configuration.KotlinSetupEnvironmentNotificationProvider.fileIsUnderKotlinSourceRoot]
     * function must handle correctly. Even though `projectParent.name == "src"`, the file should still be recognized
     * as being under a valid Kotlin source root structure.
     *
     * **Important:** This test modifies the module state temporarily. The original content
     * entries are saved and restored in a finally block to prevent affecting other tests.
     */
    @Test
    fun testFileUnderSrcWhenProjectParentIsSrc() {
        val kotlinFileRelativePath = "/main/kotlin/App.kt"  // src/ already exists in temp dir
        createKotlinFile(kotlinFileRelativePath)

        val psiFile = myFixture.configureFromTempProjectFile(kotlinFileRelativePath)
        val virtualFile = psiFile.virtualFile
        val mainDir = virtualFile.parent?.parent ?: return

        val originalEntries = ModuleRootManager.getInstance(myFixture.module).contentEntries.toList()

        try {
            // Set content root to "main" directory so that its parent "src" becomes projectParent
            ModuleRootModificationUtil.updateModel(myFixture.module) { model ->
                model.contentEntries.forEach { model.removeContentEntry(it) }
                model.addContentEntry(mainDir)
            }

            assertFileIsUnderSourceRoots(kotlinFileRelativePath, fileShouldBeUnderSourceRoots = true)
        } finally {
            // Restore the original content entries so other tests are not affected
            ModuleRootModificationUtil.updateModel(myFixture.module) { model ->
                model.contentEntries.forEach { model.removeContentEntry(it) }
                originalEntries.forEach { original ->
                    model.addContentEntry(original.file!!)
                }
            }
        }
    }

    @Test
    fun testFileUnderSrcCommonMainKotlin() {
        val kotlinFileRelativePath = "/commonMain/kotlin/TestKotlin.kt"
        createKotlinFile(kotlinFileRelativePath)
        assertFileIsUnderSourceRoots(kotlinFileRelativePath, fileShouldBeUnderSourceRoots = true)
    }

    @Test
    fun testFileUnderSrcJsMainKotlin() {
        val kotlinFileRelativePath = "/jsMain/kotlin/TestKotlin.kt"
        createKotlinFile(kotlinFileRelativePath)
        assertFileIsUnderSourceRoots(kotlinFileRelativePath, fileShouldBeUnderSourceRoots = true)
    }

    @Test
    fun testFileNotUnderKotlinSourceRoot() {
        // File with .kt extension but in /resources instead of /kotlin - should not be recognized
        val kotlinFileRelativePath = "/main/resources/TestKotlin.kt"
        createKotlinFile(kotlinFileRelativePath)
        assertFileIsUnderSourceRoots(kotlinFileRelativePath, fileShouldBeUnderSourceRoots = false)
    }

    private fun createKotlinFile(kotlinFileRelativePath: String) {
        val kotlinFile = myFixture.addFileToProject(kotlinFileRelativePath, "")
        myFixture.openFileInEditor(kotlinFile.virtualFile)
    }

    private fun assertFileIsUnderSourceRoots(kotlinFileRelativePath: String, fileShouldBeUnderSourceRoots: Boolean) {
        val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
        val selectedFile = selectedEditor?.file ?: error("file $kotlinFileRelativePath has to be in the editor")
        assertEquals(
            fileShouldBeUnderSourceRoots,
            KotlinSetupEnvironmentNotificationProvider().fileIsUnderKotlinSourceRoot(selectedFile, project)
        )
    }
}
