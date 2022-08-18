// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.stubBuilder

import com.google.common.io.Files
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinClsStubBuilder
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.stubs.AbstractStubBuilderTest
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.stubs.elements.KtFileStubBuilder
import org.jetbrains.kotlin.utils.addIfNotNull
import org.junit.Assert
import java.io.File

abstract class AbstractClsStubBuilderTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinJdkAndLibraryProjectDescriptor(
        listOf(TestKotlinArtifacts.kotlinAnnotationsJvm),
        emptyList()
    )

    fun doTest(sourcePath: String) {
        val ktFile = File("$sourcePath/${lastSegment(sourcePath)}.kt")
        val jvmFileName = if (ktFile.exists()) {
            val ktFileText = ktFile.readText()
            InTextDirectivesUtils.findStringWithPrefixes(ktFileText, "JVM_FILE_NAME:")
        } else {
            null
        }

        val txtFilePath = File("$sourcePath/${lastSegment(sourcePath)}.txt")

        testWithEnabledStringTable(sourcePath, jvmFileName, txtFilePath)
        testWithDisabledStringTable(sourcePath, jvmFileName, txtFilePath)
    }

    private fun testWithEnabledStringTable(sourcePath: String, classFileName: String?, txtFile: File?) {
        doTest(sourcePath, true, classFileName, txtFile)
    }

    private fun testWithDisabledStringTable(sourcePath: String, classFileName: String?, txtFile: File?) {
        doTest(sourcePath, false, classFileName, txtFile)
    }

    protected fun doTest(sourcePath: String, useStringTable: Boolean, classFileName: String?, txtFile: File?) {
        val classFile = getClassFileToDecompile(sourcePath, useStringTable, classFileName)
        testClsStubsForFile(classFile, txtFile)
    }

    protected fun testClsStubsForFile(classFile: VirtualFile, txtFile: File?) {
        val stubTreeFromCls = KotlinClsStubBuilder().buildFileStub(FileContentImpl.createByFile(classFile))!!
        myFixture.configureFromExistingVirtualFile(classFile)
        val psiFile = myFixture.file
        val stubTreeFromDecompiledText = KtFileStubBuilder().buildStubTree(psiFile)
        val expectedText = stubTreeFromDecompiledText.serializeToString()
        Assert.assertEquals(expectedText, stubTreeFromCls.serializeToString())
        if (txtFile != null) {
            KotlinTestUtils.assertEqualsToFile(txtFile, expectedText)
        }
    }

    private fun getClassFileToDecompile(sourcePath: String, isUseStringTable: Boolean, classFileName: String?): VirtualFile {
        val outDir = KotlinTestUtils.tmpDir("libForStubTest-$sourcePath")

        val extraOptions = ArrayList<String>()
        extraOptions.add("-Xallow-kotlin-package")
        if (isUseStringTable) {
            extraOptions.add("-Xuse-type-table")
        }

        KotlinCompilerStandalone(
            sources = listOf(File(sourcePath)),
            target = outDir,
            options = extraOptions,
            classpath = listOf(TestKotlinArtifacts.kotlinAnnotationsJvm)
        ).compile()

        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outDir)!!
        return root.findClassFileByName(classFileName ?: lastSegment(sourcePath))
    }

    private fun lastSegment(sourcePath: String): String {
        return Files.getNameWithoutExtension(sourcePath.split('/').last { !it.isEmpty() })!!
    }
}

fun StubElement<out PsiElement>.serializeToString(): String {
    return AbstractStubBuilderTest.serializeStubToString(this)
}

fun VirtualFile.findClassFileByName(className: String): VirtualFile {
    val files = LinkedHashSet<VirtualFile>()
    VfsUtilCore.iterateChildrenRecursively(
        this,
        { virtualFile ->
            virtualFile.isDirectory || virtualFile.name == "$className.class"
        },
        { virtualFile ->
            if (!virtualFile.isDirectory) files.addIfNotNull(virtualFile); true
        })

    return files.single()
}
