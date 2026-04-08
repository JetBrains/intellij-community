// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.GlobalState
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.registerExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.io.File
import kotlin.jvm.java
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

abstract class AbstractScriptHighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest() {
    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        runBlocking {
            files.forEach { KotlinScriptService.getInstance(project).load(it.virtualFile) }
        }

        super.doMultiFileTest(files, globalDirectives)
    }

    override fun runInDispatchThread(): Boolean = false

    protected open val customDefinitionsProvider: CustomDefinitionProviderForTest? = null

    override fun setUp() {
        setUpWithKotlinPlugin {
            val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
            myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture())
            val javaModuleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
            javaModuleFixtureBuilder.addContentRoot(myFixture.tempDirPath)
            myFixture.setUp()
        }

        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)
        invalidateLibraryCache(project)

        runBlocking(Dispatchers.EDT) {
            edtWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = IdeaTestUtil.getMockJdk17()
            }
        }

        customDefinitionsProvider?.let {
            project.registerExtension(ScriptDefinitionsProvider.EP_NAME, it, testRootDisposable)
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

    protected open class CustomDefinitionProviderForTest(override val id: String) : ScriptDefinitionsProvider {
        override fun getDefinitionClasses(): Iterable<String> = emptyList()
        override fun getDefinitionsClassPath(): Iterable<File> = emptyList()
        override fun useDiscovery(): Boolean = false
    }
}