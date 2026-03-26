// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Assert

class QuickDocJarTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    fun testWithSources_showsDefaults() {
        val classesRoot = JarFileSystem.getInstance().refreshAndFindFileByPath("$IDEA_TEST_DATA_DIR/lib/HasDefaults.jar!/")
        PsiTestUtil.addProjectLibrary(myFixture.module, "mylib", listOf(classesRoot), listOfNotNull(classesRoot?.findFileByRelativePath("/main/kotlin")))

        val psiFile = myFixture.addFileToProject("my/great/app/MyFile.kt", """
            package my.great.app
            import com.example.HasDefaults

            fun myFunction() {
              HasDefaults.met<caret>hod(
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        val target = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(editor, file, editor.caretModel.offset)
            .firstOrNull() ?: error("No documentation target found")
        val html = computeDocumentationBlocking(target.createPointer())?.html
        checkNotNull(html)
        Assert.assertFalse(html.contains("..."))
        Assert.assertTrue(html.contains("A_DEFAULT"))
        Assert.assertTrue(html.contains("B_DEFAULT"))
    }
}
