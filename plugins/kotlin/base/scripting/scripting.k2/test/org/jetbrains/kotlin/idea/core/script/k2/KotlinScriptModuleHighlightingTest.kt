// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.highlighter.ALLOW_ERRORS
import org.jetbrains.kotlin.idea.highlighter.CHECK_SYMBOL_NAMES
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

/** Checks which scripts see the classes of the module that contains them: a content root `module` with one source folder `module/src`. */
class KotlinScriptModuleHighlightingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun runInDispatchThread(): Boolean = false

    fun testRegularScriptInSourceFolder() = checkScript("module/src/script.kts", allowErrors = true)

    fun testRegularScriptOutsideSourceFolders() = checkScript("module/script.kts")

    fun testTeamcityScriptInSourceFolder() = checkScript("module/src/.teamcity/settings.kts")

    fun testTeamcityScriptOutsideSourceFolders() = checkScript("module/.teamcity/settings.kts")

    private fun checkScript(path: String, allowErrors: Boolean = false) {
        runInEdtAndWait {
            PsiTestUtil.addContentRoot(module, createDirectory("module"))
            PsiTestUtil.addSourceRoot(module, createDirectory("module/src"))
        }
        myFixture.addFileToProject("module/src/Alpha.kt", "class Alpha")
        val script = myFixture.addFileToProject(path, "Alpha()")

        runBlocking {
            KotlinScriptService.getInstance(project).load(script.virtualFile)
        }

        val directives = Directives().apply {
            put(CHECK_SYMBOL_NAMES, null)
            put(HIGHLIGHTER_ATTRIBUTES_KEY, null)
            if (allowErrors) put(ALLOW_ERRORS, null)
        }
        runInEdtAndWait {
            checkHighlighting(script, TEST_DATA.resolve("${getTestName(true)}.kts.highlighting"), directives, project)
        }
    }

    private fun createDirectory(relativePath: String): VirtualFile =
        runWriteAction { myFixture.tempDirFixture.findOrCreateDir(relativePath) }

    private companion object {
        const val HIGHLIGHTER_ATTRIBUTES_KEY = "HIGHLIGHTER_ATTRIBUTES_KEY"
        val TEST_DATA: File = KotlinRoot.DIR.resolve("base/scripting/scripting.k2/testData/highlighting")
    }
}
