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
    protected fun doTest(packageFqName: String, classNameForDirectorySearch: String? = null): String {
        val stubTreeFromDecompiler = configureAndBuildFileStub(packageFqName, classNameForDirectorySearch)
        val stubTreeFromDecompiledText = KtFileStubBuilder().buildStubTree(myFixture.file)
        val expectedText = stubTreeFromDecompiledText.serializeToString()

        // KT-74547: K1 descriptors do not support MustUseReturnValue feature recorded to metadata
        val actualText = stubTreeFromDecompiler.serializeToString().replace(" MustUseReturnValue", "")
        Assert.assertEquals("Stub mismatch for package $packageFqName", expectedText, actualText)
        return expectedText
    }

    abstract fun configureAndBuildFileStub(packageFqName: String, classNameForDirectorySearch: String? = null): PsiFileStub<*>

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}

@RunWith(JUnit38ClassRunner::class)
class BuiltInDecompilerTest : AbstractBuiltInDecompilerTest() {
    override fun getProjectDescriptor(): KotlinWithJdkAndRuntimeLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceNoSources()

    override fun configureAndBuildFileStub(packageFqName: String, classNameForDirectorySearch: String?): PsiFileStub<*> {
        val dirInRuntime = findDir(packageFqName, project, classNameForDirectorySearch)
        val kotlinBuiltInsVirtualFile = dirInRuntime.children.single { it.extension == BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION }
        myFixture.configureFromExistingVirtualFile(kotlinBuiltInsVirtualFile)
        return KotlinBuiltInDecompiler().stubBuilder.buildFileStub(FileContentImpl.createByFile(kotlinBuiltInsVirtualFile))!!
    }

    fun testBuiltInStubTreeEqualToStubTreeFromDecompiledText() {
        doTest("kotlin", classNameForDirectorySearch = "Int")
        doTest("kotlin.collections", classNameForDirectorySearch = "List") // TODO(kirpichenkov): remove the hack once KTIJ-28858 is fixed
    }
}

@RunWith(JUnit38ClassRunner::class)
class BuiltInDecompilerForWrongMetadataVersionTest : AbstractBuiltInDecompilerTest() {
    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("decompiler/builtins")

    override fun configureAndBuildFileStub(packageFqName: String, classNameForDirectorySearch: String?): PsiFileStub<*> {
        myFixture.configureByFile(BuiltInSerializerProtocol.getBuiltInsFilePath(FqName(packageFqName)))
        return KotlinBuiltInDecompiler().stubBuilder.buildFileStub(FileContentImpl.createByFile(myFixture.file.virtualFile))!!
    }

    fun testStubTreesEqualForIncompatibleAbiVersion() {
        val serializedStub = doTest("test").replace(BuiltInsBinaryVersion.INSTANCE.toString(), $$"$VERSION$")
        KotlinTestUtils.assertEqualsToFile(
            File(testDataDirectory, "test.text"),
            myFixture.file.text.replace(BuiltInsBinaryVersion.INSTANCE.toString(), $$"$VERSION$")
        )
        KotlinTestUtils.assertEqualsToFile(File(testDataDirectory, "test.stubs"), serializedStub)
    }
}
