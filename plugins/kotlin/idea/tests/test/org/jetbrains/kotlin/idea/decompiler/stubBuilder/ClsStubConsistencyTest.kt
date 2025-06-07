// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.decompiler.stubBuilder

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.decompiler.psi.text.buildDecompiledTextForClassFile
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinClsStubBuilder
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.stubs.elements.KtFileStubBuilder
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class ClsStubConsistencyTest : KotlinLightCodeInsightFixtureTestCase() {
    private fun doTest(id: ClassId) {
        val packageFile = VirtualFileFinder.SERVICE.getInstance(project, module = null).findVirtualFileWithHeader(id)
            ?: throw AssertionError("File not found for id: $id")
        val decompiledText = buildDecompiledTextForClassFile(packageFile).text
        val fileWithDecompiledText = KtPsiFactory(project).createFile(decompiledText)
        val compiledFile = KtFile(fileWithDecompiledText.viewProvider, isCompiled = true)
        val stubTreeFromDecompiledText = KtFileStubBuilder().buildStubTree(compiledFile)
        val expectedText = stubTreeFromDecompiledText.serializeToString()

        val fileStub = KotlinClsStubBuilder().buildFileStub(FileContentImpl.createByFile(packageFile))!!
        Assert.assertEquals(expectedText, fileStub.serializeToString())
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testConsistency() {
        doTest(ClassId.topLevel(FqName("kotlin.collections.CollectionsKt")))
    }
}
