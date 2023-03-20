// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.stubBuilder

import com.intellij.psi.stubs.PsiFileStub
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInDecompiler
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.metadata.builtins.BuiltInsBinaryVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.stubs.elements.KtFileStubBuilder
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

abstract class AbstractBuiltInDecompilerTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun doTest(packageFqName: String): String {
        val stubTreeFromDecompiler = configureAndBuildFileStub(packageFqName)
        val stubTreeFromDecompiledText = KtFileStubBuilder().buildStubTree(myFixture.file)
        val expectedText = stubTreeFromDecompiledText.serializeToString()
        Assert.assertEquals("Stub mismatch for package $packageFqName", expectedText, stubTreeFromDecompiler.serializeToString())
        return expectedText
    }

    abstract fun configureAndBuildFileStub(packageFqName: String): PsiFileStub<*>

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}

@RunWith(JUnit38ClassRunner::class)
class BuiltInDecompilerTest : AbstractBuiltInDecompilerTest() {
    override fun getProjectDescriptor(): KotlinWithJdkAndRuntimeLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceNoSources()

    override fun configureAndBuildFileStub(packageFqName: String): PsiFileStub<*> {
        val dirInRuntime = findDir(packageFqName, project)
        val kotlinBuiltInsVirtualFile = dirInRuntime.children.single { it.extension == BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION }
        myFixture.configureFromExistingVirtualFile(kotlinBuiltInsVirtualFile)
        return KotlinBuiltInDecompiler().stubBuilder.buildFileStub(FileContentImpl.createByFile(kotlinBuiltInsVirtualFile))!!
    }

    fun testBuiltInStubTreeEqualToStubTreeFromDecompiledText() {
        doTest("kotlin")
        doTest("kotlin.collections")
    }
}

@RunWith(JUnit38ClassRunner::class)
class BuiltInDecompilerForWrongAbiVersionTest : AbstractBuiltInDecompilerTest() {
    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("decompiler/builtins")

    override fun configureAndBuildFileStub(packageFqName: String): PsiFileStub<*> {
        myFixture.configureByFile(BuiltInSerializerProtocol.getBuiltInsFilePath(FqName(packageFqName)))
        return KotlinBuiltInDecompiler().stubBuilder.buildFileStub(FileContentImpl.createByFile(myFixture.file.virtualFile))!!
    }

    fun testStubTreesEqualForIncompatibleAbiVersion() {
        val serializedStub = doTest("test")
        KotlinTestUtils.assertEqualsToFile(
            File(testDataPath + "test.text"),
            myFixture.file.text.replace(BuiltInsBinaryVersion.INSTANCE.toString(), "\$VERSION\$")
        )
        KotlinTestUtils.assertEqualsToFile(File(testDataPath + "test.stubs"), serializedStub)
    }
}
