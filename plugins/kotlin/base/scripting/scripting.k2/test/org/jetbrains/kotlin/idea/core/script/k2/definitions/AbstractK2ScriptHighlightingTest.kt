// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.KotlinScriptResolutionService
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import kotlin.jvm.java

abstract class AbstractK2ScriptHighlightingTest : AbstractHighlightingMetaInfoTest() {
    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        runBlocking {
            KotlinScriptResolutionService.getInstance(project).process(files.map { it.virtualFile })
        }

        super.doMultiFileTest(files, globalDirectives)
    }

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        setUpWithKotlinPlugin {
            val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
            myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture())
            projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
            myFixture.setUp()
        }

        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)
        invalidateLibraryCache(project)

        runBlocking(Dispatchers.EDT) {
            edtWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = IdeaTestUtil.getMockJdk17()
            }
        }
    }

    override fun doTest(testDataPath: String) {
        val testKtFile = dataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2,
            additionalFilesExtensions = arrayOf(HIGHLIGHTING_EXTENSION)
        ) {
            super.doTest(testDataPath)
        }
    }
}