// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
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