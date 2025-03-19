// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions.assertTrue
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.params.ParameterizedTest
import kotlin.io.path.nameWithoutExtension


@TestRoot("base/fir/project-structure/")
@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("testData/gradleScript")
class GradleScriptProjectStructureTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @GradleTestSource("8.11")
    @TestMetadata("fromWizard.test")
    fun testSimple(gradleVersion: GradleVersion) {
        checkProjectStructure(gradleVersion)
    }

    private fun checkProjectStructure(gradleVersion: GradleVersion) {
        test(gradleVersion, GRADLE_KOTLIN_FIXTURE) {
            val mainFile = mainTestDataPsiFile

            runInEdtAndWait {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(mainFile)
            }

            val allModules = runReadAction { collectModules() }
            runReadAction { validateScriptModules(allModules) }

            val (txt, mermaid) = runReadAction {
                val txt = KaModuleStructureTxtRenderer.render(allModules)
                val mermaid = KaModuleStructureMermaidRenderer.render(allModules)
                txt to mermaid
            }

            val testDataFilePath = dataFile().toPath()
            KotlinTestUtils.assertEqualsToFile(testDataFilePath.resolveSibling(testDataFilePath.nameWithoutExtension + ".txt"), txt)
            KotlinTestUtils.assertEqualsToFile(testDataFilePath.resolveSibling(testDataFilePath.nameWithoutExtension + ".mmd"), mermaid)
        }
    }

    private fun validateScriptModules(modules: List<KaModule>) {
        val allVirtualFiles = getAllVirtualFiles()
        for (module in modules.filterIsInstance<KaScriptModule>()) {
            val scope = module.contentScope
            assertTrue(module.file.virtualFile in scope)

            val excludedFiles = allVirtualFiles - module.file.virtualFile
            val excludedFilesInScope = excludedFiles.filter { it in scope }
            assertTrue(excludedFilesInScope.isEmpty()) {
                "KaScriptModuleScope should contain only the script file itself. " +
                        "But some files are excluded from the script module scope: ${excludedFilesInScope.joinToString { it.path }}"
            }
        }
    }

    private fun getAllVirtualFiles(): Set<VirtualFile> {
        return buildSet {
            addAll(FilenameIndex.getAllFilesByExt(project, "kt"))
            addAll(FilenameIndex.getAllFilesByExt(project, "kts"))
            addAll(FilenameIndex.getAllFilesByExt(project, "java"))
            addAll(FilenameIndex.getAllFilesByExt(project, "class"))
        }
    }


    private fun collectModules(): List<KaModule> {
        val files = testDataFiles.map { getFile(it.path).getPsiFile(project) }
        val modules = files.map { it.getKaModule(project, useSiteModule = null) }
        return modules.computeDependenciesClosure()
            // should not be here empty, mitigation of KT-74010
            .filter { it !is KaBuiltinsModule }
    }
}