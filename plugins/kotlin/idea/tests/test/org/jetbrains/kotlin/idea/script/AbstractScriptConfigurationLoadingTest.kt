// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptConfigurationManagerExtensions
import org.jetbrains.kotlin.idea.core.script.configuration.loader.FileContentsDependentConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.configuration.testingBackgroundExecutor
import org.jetbrains.kotlin.idea.core.script.configuration.utils.testScriptConfigurationNotification
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractScriptConfigurationLoadingTest : AbstractScriptConfigurationTest() {
    lateinit var scriptConfigurationManager: CompositeScriptConfigurationManager

    companion object {
        private var occurredLoadings = 0
        private var currentLoadingScriptConfigurationCallback: (() -> Unit)? = null

        @JvmStatic
        @Suppress("unused")
        fun loadingScriptConfigurationCallback() {
            // this method is called from testData/script/definition/loading/async/template/template.kt
            currentLoadingScriptConfigurationCallback?.invoke()
            occurredLoadings++
        }
    }

    override fun setUp() {
        super.setUp()
        testScriptConfigurationNotification = true
        ApplicationManager.getApplication().isScriptChangesNotifierDisabled = false

        scriptConfigurationManager = project.getService(ScriptConfigurationManager::class.java) as CompositeScriptConfigurationManager
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { super.tearDown() },
            ThrowableRunnable { testScriptConfigurationNotification = false },
            ThrowableRunnable { ApplicationManager.getApplication().isScriptChangesNotifierDisabled = true },
            ThrowableRunnable { 
                occurredLoadings = 0
                currentLoadingScriptConfigurationCallback = null
            }
        )
    }

    override fun setUpTestProject() {
        addExtensionPointInTest(
            DefaultScriptConfigurationManagerExtensions.LOADER,
            project,
            FileContentsDependentConfigurationLoader(project),
            testRootDisposable
        )

        configureScriptFile(IDEA_TEST_DATA_DIR.resolve("script/definition/loading/async"))
    }

    override fun loadScriptConfigurationSynchronously(script: VirtualFile) {
        // do nothings
    }

    protected fun assertAndDoAllBackgroundTasks() {
        assertDoAllBackgroundTaskAndDoWhileLoading { }
    }

    protected fun assertDoAllBackgroundTaskAndDoWhileLoading(actions: () -> Unit) {
        assertTrue("some script configuration loading tasks should be scheduled", doAllBackgroundTasksWith(actions))
    }

    protected fun doAllBackgroundTasksWith(actions: () -> Unit): Boolean {
        return scriptConfigurationManager.testingBackgroundExecutor.doAllBackgroundTaskWith {
            currentLoadingScriptConfigurationCallback = {
                actions()
                currentLoadingScriptConfigurationCallback = null
            }
        }
    }

    protected fun assertNoBackgroundTasks() {
        assertTrue("script configuration loading tasks should not be scheduled", scriptConfigurationManager.testingBackgroundExecutor.noBackgroundTasks())
    }

    protected fun assertAppliedConfiguration(contents: String, file: KtFile = myFile as KtFile) {
        val secondConfiguration = scriptConfigurationManager.getConfiguration(file)!!
        assertEquals(
            "configuration \"$contents\" should be applied",
            StringUtilRt.convertLineSeparators(contents),
            StringUtilRt.convertLineSeparators(secondConfiguration.defaultImports.single().let {
                check(it.startsWith("x_"))
                it.removePrefix("x_")
            })
        )
    }

    protected fun makeChanges(contents: String, file: KtFile = myFile as KtFile) {
        changeContents(contents)

        scriptConfigurationManager.default.ensureUpToDatedConfigurationSuggested(file)
    }

    protected fun changeContents(contents: String, file: PsiFile = myFile) {
        runWriteAction {
            VfsUtil.saveText(file.virtualFile, contents)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFile = psiManager.findFile(file.virtualFile)
        }
    }

    protected fun assertReports(expected: String, file: KtFile = myFile as KtFile) {
        val actual = getScriptReports(file.virtualFile).single().message
        assertEquals("reports", expected, actual)
    }

    protected fun assertSuggestedConfiguration(file: KtFile = myFile as KtFile) {
        assertTrue("new configuration should be suggested", file.virtualFile.hasSuggestedScriptConfiguration(project))
    }

    protected fun assertAndApplySuggestedConfiguration(file: KtFile = myFile as KtFile) {
        assertTrue("new configuration should be suggested", file.virtualFile.applySuggestedScriptConfiguration(project))
    }

    protected fun assertNoSuggestedConfiguration(file: KtFile = myFile as KtFile) {
        assertFalse("new configuration should not be suggested", file.virtualFile.applySuggestedScriptConfiguration(project))
    }

    protected fun assertNoLoading() {
        assertEquals("loading should not be occurred", 0, occurredLoadings)
        occurredLoadings = 0
    }

    protected fun assertSingleLoading() {
        assertEquals("exactly single loading should occur", 1, occurredLoadings)
        occurredLoadings = 0
    }

    protected open fun assertAndLoadInitialConfiguration(file: KtFile = myFile as KtFile) {
        assertNull(scriptConfigurationManager.getConfiguration(file))
        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAppliedConfiguration(file.text, file)

        checkHighlighting(file)
    }

    protected inline fun <reified T : Any> copyFromTestdataToProject(file: File): T {
        createFileAndSyncDependencies(file)
        return (myFile as? T) ?: error("Couldn't configure project by ${file.path}")
    }

    protected fun createFileInProject(path: String): File {
        val file = File(project.basePath, path)
        file.parentFile.mkdirs()
        file.createNewFile()
        return file
    }


}
