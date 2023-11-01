// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor.quickDoc

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.KotlinDocumentationProvider
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Assert

class QuickDocJarTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testWithSources_showsDefaults() {
        val classesRoot = JarFileSystem.getInstance().refreshAndFindFileByPath("$IDEA_TEST_DATA_DIR/lib/HasDefaults.jar!/")
        PsiTestUtil.addProjectLibrary(myFixture.module, "mylib", listOf(classesRoot), listOf(classesRoot))

        val psiFile = myFixture.addFileToProject("my/great/app/MyFile.kt", """
            package my.great.app
            import com.example.HasDefaults
            
            fun myFunction() {
              HasDefaults.met<caret>hod(
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        val element = myFixture.elementAtCaret

        val html = KotlinDocumentationProvider().generateDoc(element, null)
        checkNotNull(html)
        Assert.assertFalse(html.contains("..."))
        Assert.assertTrue(html.contains("A_DEFAULT"))
        Assert.assertTrue(html.contains("B_DEFAULT"))
    }
}