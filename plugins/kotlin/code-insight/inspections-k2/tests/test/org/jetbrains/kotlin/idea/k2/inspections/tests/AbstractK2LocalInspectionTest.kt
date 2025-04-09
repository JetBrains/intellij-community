// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.core.script.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptConfigurationHandler
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.getConfigurationResolver
import org.jetbrains.kotlin.idea.fir.K2DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class AbstractK2LocalInspectionTest : AbstractLocalInspectionTest() {

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val inspectionFileName: String = ".k2Inspection"

    override val skipErrorsBeforeCheckDirectives: List<String>
        get() = super.skipErrorsBeforeCheckDirectives + K2DirectiveBasedActionUtils.DISABLE_K2_ERRORS_DIRECTIVE

    override val skipErrorsAfterCheckDirectives: List<String>
        get() = super.skipErrorsAfterCheckDirectives + K2DirectiveBasedActionUtils.DISABLE_K2_ERRORS_DIRECTIVE

    override fun checkForErrorsBefore(mainFile: File, ktFile: KtFile, fileText: String) {
        K2DirectiveBasedActionUtils.checkForErrorsBefore(mainFile, ktFile, fileText)
    }

    override fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        K2DirectiveBasedActionUtils.checkForErrorsAfter(mainFile, ktFile, fileText)
    }

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory)

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }

    override fun getAfterTestDataAbsolutePath(mainFileName: String): Path {
        val k2Extension = IgnoreTests.FileExtension.K2
        val k2FileName = mainFileName.removeSuffix(".kt").removeSuffix(".$k2Extension") + ".$k2Extension.kt.after"
        val k2FilePath = testDataDirectory.toPath() / k2FileName
        if (k2FilePath.exists()) return k2FilePath

        return super.getAfterTestDataAbsolutePath(mainFileName)
    }

    override fun doTest(path: String) {
        val mainFile = File(dataFilePath(fileName()))

        val extraFileNames = findExtraFilesForTest(mainFile)

        myFixture.configureByFiles(*(listOf(mainFile.name) + extraFileNames).toTypedArray()).first()

        val ktFile = myFixture.file as? KtFile
        ktFile?.let { processKotlinScriptIfNeeded(ktFile) }

        super.doTest(path)
    }

    private fun processKotlinScriptIfNeeded(ktFile: KtFile) {
        if (!ktFile.isScript()) return

        project.replaceService(
            DefaultScriptConfigurationHandler::class.java,
            DefaultScriptConfigurationHandlerForTests(project), testRootDisposable
        )

        runWithModalProgressBlocking(project, "AbstractK2LocalInspectionTest") {
            ktFile.findScriptDefinition()?.let {
                it.getConfigurationResolver(project).create(ktFile.alwaysVirtualFile, it)
            }
        }
    }

    // kotlin scripts require adjusting project model which is not possible for lightweight test fixture
    private class DefaultScriptConfigurationHandlerForTests(testProject: Project) :
        DefaultScriptConfigurationHandler(testProject, CoroutineScope(EmptyCoroutineContext)) {
        override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {}

        override fun isModuleExist(
            project: Project,
            scriptFile: VirtualFile,
            definition: ScriptDefinition
        ): Boolean = true
    }
}