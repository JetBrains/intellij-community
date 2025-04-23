// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.GlobalState
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2ScriptHighlightingTest : AbstractHighlightingMetaInfoTest() {
    override fun doMultiFileTest(
        files: List<PsiFile>, globalDirectives: Directives
    ) {
        runBlocking {
            DefaultScriptResolutionStrategy.getInstance(project).execute(*(files.mapNotNull { it as? KtFile }.toTypedArray())).join()
        }

        super.doMultiFileTest(files, globalDirectives)
    }

    override fun getProject(): Project {
        return super.getProject()
    }

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        GlobalState.checkSystemStreams()
        setupTempDir()

        setUpWithKotlinPlugin {
            val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
            myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture())
            val moduleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
            moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath())
            myFixture.setUp()
        }

        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)
        EditorTracker.getInstance(project)
        invalidateLibraryCache(project)
    }


    override fun doTest(unused: String) {
        val testKtFile = dataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2,
            additionalFilesExtensions = arrayOf(HIGHLIGHTING_EXTENSION)
        ) { // warnings are not supported yet
            super.doTest(unused)
        }
    }
}