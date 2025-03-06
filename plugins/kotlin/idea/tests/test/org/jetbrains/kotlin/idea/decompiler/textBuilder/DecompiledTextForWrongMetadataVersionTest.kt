// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.textBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.analysis.decompiler.psi.text.INCOMPATIBLE_METADATA_VERSION_GENERAL_COMMENT
import org.jetbrains.kotlin.idea.decompiler.AbstractInternalCompiledClassesTest
import org.jetbrains.kotlin.idea.decompiler.stubBuilder.findClassFileByName
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class DecompiledTextForWrongMetadataVersionTest : AbstractInternalCompiledClassesTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJdkAndLibraryProjectDescriptor(File(IDEA_TEST_DATA_DIR.absolutePath + "/wrongMetadataVersionLib/bin"))
    }

    fun testSyntheticClassIsInvisibleWrongMetadataVersion() = doTestNoPsiFilesAreBuiltForSyntheticClasses()

    fun testClassWithWrongMetadataVersion() = doTest("ClassWithWrongMetadataVersion")

    fun testPackagePartWithWrongMetadataVersion() = doTest("Wrong_packageKt")

    fun doTest(name: String) {
        val root = findTestLibraryRoot(module!!)!!
        checkFileWithWrongMetadataVersion(root.findClassFileByName(name))
    }

    private fun checkFileWithWrongMetadataVersion(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        Assert.assertTrue(psiFile is KtClsFile)
        val decompiledText = psiFile!!.text!!
        Assert.assertTrue(decompiledText.contains(INCOMPATIBLE_METADATA_VERSION_GENERAL_COMMENT))
    }
}
