// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.stubBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.BinaryLightVirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinDecompiledFileViewProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinClsStubBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.stubs.elements.KtFileStubBuilder
import org.junit.Assert
import java.io.File

abstract class AbstractLoadJavaClsStubTest : KotlinLightCodeInsightFixtureTestCase() {
    @Throws(Exception::class)
    protected fun doTestCompiledKotlin(absoluteTestDataPath: String) {
        myFixture.configureByFile(File(absoluteTestDataPath))

        val ktFile = myFixture.file as KtFile
        val analysisResult = ktFile.analyzeWithAllCompilerChecks()

        val configuration = CompilerConfiguration().apply { languageVersionSettings = file.languageVersionSettings }

        val state = GenerationState.Builder(
            project, ClassBuilderFactories.BINARIES, analysisResult.moduleDescriptor,
            analysisResult.bindingContext, listOf(ktFile), configuration
        ).build()

        try {
            KotlinCodegenFacade.compileCorrectFiles(state)
            val outputFiles = state.factory

            val lightFiles = HashMap<String, VirtualFile>()

            fun addDirectory(filePath: String) {
                assert(filePath.startsWith('/'))

                lightFiles.getOrPut(filePath) {
                    object : LightVirtualFile(filePath) {
                        override fun isDirectory() = true
                        override fun getParent() = lightFiles[PathUtil.getParentPath(filePath)]
                        override fun findChild(name: String) = lightFiles["$filePath/$name"]
                    }
                }
            }

            fun addFile(filePath: String, content: ByteArray) {
                assert(filePath.startsWith('/'))

                val pathSegments = filePath.drop(1).split('/')
                repeat(pathSegments.size) { i ->
                    addDirectory("/" + pathSegments.take(i).joinToString("/"))
                }

                lightFiles[filePath] = object : BinaryLightVirtualFile(filePath, content) {
                    override fun getParent() = lightFiles[PathUtil.getParentPath(filePath)]
                }
            }

            addDirectory("/")

            for (file in outputFiles.asList()) {
                if (!file.relativePath.endsWith(".class")) {
                    continue
                }

                addFile("/" + file.relativePath, file.asByteArray())
            }

            val psiManager = PsiManager.getInstance(project)

            for (lightFile in lightFiles.values) {
                if (lightFile.isDirectory) {
                    continue
                }

                val fileContent = FileContentImpl.createByFile(lightFile)
                val stubTreeFromCls = KotlinClsStubBuilder().buildFileStub(fileContent) ?: continue

                val decompiledProvider = KotlinDecompiledFileViewProvider(psiManager, lightFile, false, ::KtClsFile)
                val stubsFromDeserializedDescriptors = KtFileStubBuilder().buildStubTree(KtClsFile(decompiledProvider))

                Assert.assertEquals(
                    "File: ${lightFile.name}",
                    stubsFromDeserializedDescriptors.serializeToString(),
                    stubTreeFromCls.serializeToString()
                )
            }
        } finally {
            state.destroy()
        }
    }
}