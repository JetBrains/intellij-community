// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.jvmDecompiler.KotlinBytecodeDecompiler
import org.jetbrains.kotlin.idea.jvmDecompiler.KotlinBytecodeDecompilerTask
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith
import java.io.File

@TestRoot("idea/tests")
@TestDataPath("\$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners::class)
@TestMetadata("testData/highlighter/compiled")
class CompiledFilesHighlightingTest: KotlinLightCodeInsightFixtureTestCase() {
    @TestMetadata("default/linkdata/package_kotlin.collections/26_collections.knm")
    fun testKotlinCollectionsGroupingKtKotlinMetadata() {
        doTestWithLibraryFile(
            TestKotlinArtifacts.kotlinStdlibCommon,
            FileHighlightingSetting.SKIP_INSPECTION
        )
    }

    @TestMetadata("kotlin/time/TimeSource.class")
    fun testKotlinTimeTimeSourceClass() {
        doTestWithLibraryFile(
            TestKotlinArtifacts.kotlinStdlib,
            FileHighlightingSetting.SKIP_INSPECTION
        )
    }

    @TestMetadata("default/linkdata/package_kotlin.io/0_io.knm")
    fun testKotlinNativeLinkdataPackageKotlinIO0ioKnm() {
        doTestWithLibraryFile(
            TestKotlinArtifacts.kotlinStdlibNative,
            FileHighlightingSetting.SKIP_INSPECTION
        )
    }

    @TestMetadata("commonMain/kotlin/annotations/OptIn.kt")
    fun testDecompiledCodeKotlinAnnotationsOptInKt() {
        withLibrary(TestKotlinArtifacts.kotlinStdlib) {
            doTestWithLibraryFile(TestKotlinArtifacts.kotlinStdlibCommonSources, FileHighlightingSetting.SKIP_HIGHLIGHTING) {
                val file = PsiManager.getInstance(project).findFile(it) ?: error("unable to locate PSI for $it")
                val ktFile = file as? KtFile ?: error("file expected to be KtFile")

                val decompiledText = runBlocking(Dispatchers.Default) {
                    readAction {
                        KotlinBytecodeDecompiler.decompile(ktFile) ?: error("Cannot decompile file $ktFile")
                    }
                }

                KotlinBytecodeDecompilerTask(ktFile).generateDecompiledVirtualFile(decompiledText)
            }
        }
    }

    private fun doTestWithLibraryFile(
        libraryFile: File,
        expectedHighlightingSetting: FileHighlightingSetting,
        expectedDuplicatedHighlighting: Boolean = false,
        openFileAction: (VirtualFile) -> VirtualFile = { it }
    ) {
        val libraryExtension = libraryFile.extension
        val libraryVirtualFile =
            if (libraryExtension == "jar" || libraryExtension == "klib") {
                StandardFileSystems.jar().findFileByPath(libraryFile.absolutePath + URLUtil.JAR_SEPARATOR)
            } else {
                VirtualFileManager.getInstance().findFileByNioPath(libraryFile.toPath())
            } ?: error("unable to locate ${libraryFile.name}")
        var virtualFile: VirtualFile = libraryVirtualFile
        for (childName in fileName().split("/")) {
            virtualFile = virtualFile.findChild(childName) ?: error("unable to locate $childName in $virtualFile")
        }
        withLibrary(libraryFile) {
            val fileToOpen = openFileAction(virtualFile)
            val openedPsiFile = PsiManager.getInstance(project).findFile(fileToOpen) ?: error("unable to locate PSI for $virtualFile")
            val highlightingSetting = HighlightingSettingsPerFile.getInstance(project).getHighlightingSettingForRoot(openedPsiFile)
            assertEquals(expectedHighlightingSetting, highlightingSetting)
            myFixture.openFileInEditor(fileToOpen)
            doTest(expectedDuplicatedHighlighting)
        }
    }

    private fun withLibrary(libraryFile: File, block: () -> Unit) {
        val libraryName = "library ${libraryFile.name}"
        ConfigLibraryUtil.addLibrary(module, libraryName) {
            addRoot(libraryFile, OrderRootType.CLASSES)
        }
        try {
            block()
        } finally {
            ConfigLibraryUtil.removeLibrary(module, libraryName)
        }
    }

    private fun doTest(expectedDuplicatedHighlighting: Boolean) {
        val fileText = FileUtil.loadFile(File(dataFilePath(fileName().replace('/', '.') + ".txt")), true)
        try {
            withCustomCompilerOptions(fileText, project, module) {
                val fixture = myFixture as CodeInsightTestFixtureImpl
                fixture.canChangeDocumentDuringHighlighting(false)
                val data = ExpectedHighlightingData(DocumentImpl(fileText), true, true, true)
                data.checkSymbolNames()
                data.init()
                val check: () -> Unit = { fixture.collectAndCheckHighlighting(data) }
                if (expectedDuplicatedHighlighting) {
                    ExpectedHighlightingData.expectedDuplicatedHighlighting(check)
                } else {
                    check()
                }
            }
        } catch (e: FileComparisonFailedError) {
            val highlights =
                DaemonCodeAnalyzerImpl.getHighlights(myFixture.getDocument(myFixture.getFile()), null, myFixture.getProject())
            val text = myFixture.getFile().getText()
            println(TagsTestDataUtil.insertInfoTags(highlights, text))
            throw e
        }
    }
}