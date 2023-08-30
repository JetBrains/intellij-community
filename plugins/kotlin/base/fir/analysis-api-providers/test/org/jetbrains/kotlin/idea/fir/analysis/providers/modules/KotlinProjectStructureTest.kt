// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modules

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.assertInstanceOf
import com.intellij.util.CommonProcessors.FindProcessor
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import java.io.File

class KotlinProjectStructureTest : AbstractMultiModuleTest() {
    override fun getTestProjectJdk(): Sdk = IdeaTestUtil.getMockJdk11()

    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File = throw UnsupportedOperationException()

    fun `test source module`() {
        createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
                dir("one") {
                    dir("two") {
                        file("Main.kt", "class Main")
                    }
                }
            },
            testContentSpec = directoryContent {
                dir("three") {
                    file("Test.kt", "class Test")
                }
            },
        )

        assertKtModuleType<KtSourceModule>("Main.kt")
        assertKtModuleType<KtSourceModule>("Test.kt")
    }

    fun `test module of source directory`() {
        createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
                dir("one") {
                    dir("two") {
                        file("Main.kt", "class Main")
                    }
                }
            }
        )

        val file = getFile("Main.kt")
        val psiDirectory = file.parent as PsiDirectory

        assertKtModuleType<KtSourceModule>(psiDirectory)
    }

    fun `test out of content module`() {
        val file = createDummyFile("dummy.kt", "class A")
        assertKtModuleType<KtNotUnderContentRootModule>(file)

        createModule(
            moduleName = "m",
            resourceContentSpec = directoryContent {
                dir("wd") {
                    file("resource.kt", "class B")
                }
            },
        )

        // KTIJ-26841: Should be KtNotUnderContentRootModule as well
        assertKtModuleType<KtSourceModule>("resource.kt")
    }

    fun `test script module`() {
        val file = createDummyFile("dummy.kts", "class A")
        // It is KtNotUnderContentRootModule and not KtScriptModule due to a lack of virtual file
        assertKtModuleType<KtNotUnderContentRootModule>(file)

        createModule(
            moduleName = "m",
            resourceContentSpec = directoryContent {
                dir("wd") {
                    file("myScript.kts", "class B")
                }
            },
        )

        assertKtModuleType<KtScriptModule>("myScript.kts")
    }

    private inline fun <reified T> assertKtModuleType(element: PsiElement) {
        assertInstanceOf<T>(ProjectStructureProvider.getModule(project, element, contextualModule = null))
    }

    private inline fun <reified T> assertKtModuleType(fileName: String) {
        val file = getFile(fileName)
        assertKtModuleType<T>(file)
    }

    private fun getFile(name: String): PsiFile = findFile(name) ?: error("File $name is not found")

    private fun findFile(name: String): PsiFile? {
        val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name)
        val processor = object : FindProcessor<VirtualFile?>() {
            override fun accept(t: VirtualFile?): Boolean = t?.nameSequence == name
        }

        FileTypeIndex.processFiles(fileType, processor, GlobalSearchScope.everythingScope(project))
        return processor.foundValue?.toPsiFile(project)
    }

    private fun createModule(
        moduleName: String,
        srcContentSpec: DirectoryContentSpec? = null,
        testContentSpec: DirectoryContentSpec? = null,
        resourceContentSpec: DirectoryContentSpec? = null,
    ): Module {
        val module = createModule(moduleName)
        if (srcContentSpec != null) {
            val srcRoot = srcContentSpec.generateInVirtualTempDir()
            PsiTestUtil.addSourceContentToRoots(/* module = */ module, /* vDir = */ srcRoot, /* testSource = */ false)
        }

        if (testContentSpec != null) {
            val testRoot = testContentSpec.generateInVirtualTempDir()
            PsiTestUtil.addSourceContentToRoots(/* module = */ module, /* vDir = */ testRoot, /* testSource = */ true)
        }

        if (resourceContentSpec != null) {
            val resourceRoot = resourceContentSpec.generateInVirtualTempDir()
            PsiTestUtil.addResourceContentToRoots(/* module = */ module, /* vDir = */ resourceRoot, /* testResource = */ false)
        }

        return module
    }
}
