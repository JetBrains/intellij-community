/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.DiagnosticCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.getIdeaModelInfosCache
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractCodeMetaInfoTest
import org.jetbrains.kotlin.idea.codeMetaInfo.CodeMetaInfoTestCase
import org.jetbrains.kotlin.idea.codeMetaInfo.findCorrespondingFileInTestDir
import org.jetbrains.kotlin.idea.project.useCompositeAnalysis
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.TestRoot
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.resolve.descriptorUtil.getKotlinTypeRefiner
import org.jetbrains.kotlin.resolve.descriptorUtil.isTypeRefinementEnabled
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.types.refinement.TypeRefinement
import java.io.File

@TestRoot("idea/tests")
class MultiplatformModulesAndServicesCreationTest : AbstractCodeMetaInfoTest() {
    private companion object {
        private const val TEST_DIR_NAME_IN_TEST_DATA: String = "mppCreationOfModulesAndServices"
    }

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve(TEST_DIR_NAME_IN_TEST_DATA)

    override fun getConfigurations() = listOf(
        DiagnosticCodeMetaInfoRenderConfiguration(),
    )

    fun testCreationOfModulesAndServices() {
        val testDataDir = "testData/$TEST_DIR_NAME_IN_TEST_DATA"
        KotlinTestUtils.runTest(this::doRunTest, this, testDataDir);
    }

    fun doRunTest(testDataDir: String) {
        setupProject(File(testDataPath))
        require(project.useCompositeAnalysis) {
            "Expected enabled composite analysis for project"
        }

        runHighlightingForModules(testDataDir)
        val (modulesWithKtFiles, modulesWithoutKtFiles) = partitionModulesByKtFilePresence()

        for (populatedModule in modulesWithKtFiles) {
            runModuleAssertions(populatedModule, analyzed = true)
        }

        for (absentModule in modulesWithoutKtFiles) {
            runModuleAssertions(absentModule, analyzed = false)
        }
    }

    @OptIn(TypeRefinement::class)
    private fun runModuleAssertions(module: ModuleSourceInfo, analyzed: Boolean) {
        val moduleDescriptor = module.toDescriptor()
            ?: throw AssertionError("Descriptor is null for module: $module")

        assertTrue(
            "Type refinement is not enabled for module $module with enabled composite analysis mode",
            moduleDescriptor.isTypeRefinementEnabled()
        )

        assertTrue(
            "Unexpected ${if (analyzed) "" else "non-"}default type refiner for module: $module. " +
                    "Service components should ${if (analyzed) "" else "not "}have been initialized.",
            (moduleDescriptor.getKotlinTypeRefiner() !is KotlinTypeRefiner.Default) == analyzed
        )
    }

    private fun runHighlightingForModules(testDataDir: String) {
        val checker = CodeMetaInfoTestCase(getConfigurations(), checkNoDiagnosticError)

        for (module in ModuleManager.getInstance(project).modules) {
            for (sourceRoot in module.sourceRoots) {
                VfsUtilCore.processFilesRecursively(sourceRoot) { file ->
                    if (!file.isKotlinFile) return@processFilesRecursively true

                    checker.checkFile(file, file.findCorrespondingFileInTestDir(sourceRoot, File(testDataDir)), project)
                    true
                }
            }
        }
    }

    private fun partitionModulesByKtFilePresence(): Pair<List<ModuleSourceInfo>, List<ModuleSourceInfo>> {
        val moduleSourceInfos = getIdeaModelInfosCache(project).allModules().filterIsInstance<ModuleSourceInfo>()

        return moduleSourceInfos.partition {
            ModuleRootManager.getInstance(it.module).sourceRoots.any { sourceRoot ->
                var kotlinSourceFileFound = false

                VfsUtilCore.processFilesRecursively(sourceRoot) { file ->
                    if (file.isKotlinFile) {
                        kotlinSourceFileFound = true
                        return@processFilesRecursively false
                    }
                    true
                }

                kotlinSourceFileFound
            }
        }
    }

    private val VirtualFile.isKotlinFile: Boolean
        get() = this.fileType == KotlinFileType.INSTANCE
}
