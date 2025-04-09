// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.script.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptConfigurationHandler
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.getConfigurationResolver
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

abstract class AbstractK2HighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest(), KMPTest {

    @Deprecated("Use HIGHLIGHTING_K2_EXTENSION")
    protected val HIGHLIGHTING_FIR_EXTENSION = "highlighting.fir"
    protected val HIGHLIGHTING_K2_EXTENSION = "highlighting.k2"

    override fun getDefaultProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val psiFile = files.first()
        if (psiFile is KtFile) {
            processKotlinScriptIfNeeded(psiFile)
        }

        super.doMultiFileTest(files, globalDirectives)
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

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KMPProjectDescriptorTestUtilities.createKMPProjectDescriptor(testPlatform)
            ?: super.getProjectDescriptor()

    override val testPlatform: KMPTestPlatform
        get() = KMPTestPlatform.Unspecified

    override fun highlightingFileNameSuffix(testKtFile: File): String {
        val fileContent = testKtFile.readText()

        return if (InTextDirectivesUtils.isDirectiveDefined(fileContent, IgnoreTests.DIRECTIVES.FIR_IDENTICAL)) {
            super.highlightingFileNameSuffix(testKtFile)
        } else {
            HIGHLIGHTING_K2_EXTENSION
        }
    }

    override fun doTest(unused: String) {
        val testKtFile = dataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2,
            additionalFilesExtensions = arrayOf(HIGHLIGHTING_EXTENSION, HIGHLIGHTING_K2_EXTENSION)
        ) {
            // warnings are not supported yet
            super.doTest(unused)

            IgnoreTests.cleanUpIdenticalK2TestFile(
                originalTestFile = testKtFile.getExpectedHighlightingFile(HIGHLIGHTING_EXTENSION),
                k2Extension = IgnoreTests.FileExtension.FIR,
                k2TestFile = testKtFile.getExpectedHighlightingFile(HIGHLIGHTING_K2_EXTENSION),
                additionalFileToMarkFirIdentical = testKtFile
            )
        }
    }
}