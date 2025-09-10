// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.SyntheticPsiFileSupport
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class KotlinHighlightingUtilsTest : LightPlatformTestCase() {
    private fun createKotlinFileAtPath(path: String): KtFile {
        val file = File(project.basePath, path.replace("/", File.separator))
        file.parentFile.mkdirs()
        file.writeText("")
        val filePath = file.toPath()
        val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath)
            ?: error("Failed to find virtual file at ${filePath}")
        return PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: error("Returned file was not a KtFile")
    }

    fun testSnippetFile() {
        val file = LightVirtualFile("AIAssistantSnippet", KotlinLanguage.INSTANCE, "")
        val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile
            ?: error("Returned snippet file was not a KtFile")
        assertTrue(psiFile.shouldHighlightFile())
    }

    fun testFileOutsideContentRoot() {
        val file = createKotlinFileAtPath("Main.kt")
        assertFalse(file.shouldHighlightFile())
    }

    fun testFileUnderContentRoot() {
        val file = createKotlinFileAtPath("src/main/kotlin/Main.kt")
        val containingFolder = file.virtualFile.parent
        WriteAction.run<Throwable> {
            ModuleRootManager.getInstance(project.modules.first()).modifiableModel.apply {
                addContentEntry(containingFolder).addSourceFolder(containingFolder, false)
            }.commit()
        }
        assertTrue(file.shouldHighlightFile())
    }

    fun testMarkedAsOutsider() {
        val file = createKotlinFileAtPath("src/main/kotlin/Main.kt")
        SyntheticPsiFileSupport.markFile(file.virtualFile)
        assertTrue(file.shouldHighlightFile())
    }

    fun testCodeFragmentWithoutContext() {
        val codeFragment = KtExpressionCodeFragment(project, "Test.kt", "", null, null)
        assertFalse(codeFragment.shouldHighlightFile())
    }

    fun testCodeFragmentWithContext() {
        val context = KtExpressionCodeFragment(project, "Test.kt", "", null, null)
        val codeFragment = KtExpressionCodeFragment(project, "Test.kt", "", null, context)
        assertTrue(codeFragment.shouldHighlightFile())
    }
}