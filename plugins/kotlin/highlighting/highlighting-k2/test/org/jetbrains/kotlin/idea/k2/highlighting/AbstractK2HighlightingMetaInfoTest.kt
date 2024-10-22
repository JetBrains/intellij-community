// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.registerExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.script.SCRIPT_CONFIGURATIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.CommonScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurations
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK2HighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest(), KMPTest {

    @Deprecated("Use HIGHLIGHTING_K2_EXTENSION")
    protected val HIGHLIGHTING_FIR_EXTENSION = "highlighting.fir"
    protected val HIGHLIGHTING_K2_EXTENSION = "highlighting.k2"

    override fun getDefaultProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val psiFile = files.first()
        if (psiFile is KtFile && psiFile.isScript()) {
            val dependenciesSource = object : CommonScriptConfigurationsSource(project, CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                override suspend fun updateModules(
                    dependencies: ScriptConfigurations,
                    storage: MutableEntityStorage?) {
                    //do nothing because adding modules is not permitted in light tests
                }
            }

            project.registerExtension(SCRIPT_CONFIGURATIONS_SOURCES, dependenciesSource, testRootDisposable)

            val script = BaseScriptModel(psiFile.virtualFile)
            runWithModalProgressBlocking(project, "Testing") {
                dependenciesSource.updateDependenciesAndCreateModules(setOf(script))
            }
        }

        super.doMultiFileTest(files, globalDirectives)
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