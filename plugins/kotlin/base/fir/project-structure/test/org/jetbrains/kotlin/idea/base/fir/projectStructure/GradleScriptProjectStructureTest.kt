// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
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
    @BaseGradleVersionSource
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

            val (txt, mermaid) = runReadAction {
                val allModules = collectModules()
                val txt = KaModuleStructureTxtRenderer.render(allModules)
                val mermaid = KaModuleStructureMermaidRenderer.render(allModules)
                txt to mermaid
            }

            val testDataFilePath = dataFile().toPath()
            KotlinTestUtils.assertEqualsToFile(testDataFilePath.resolveSibling(testDataFilePath.nameWithoutExtension + ".txt"), txt)
            KotlinTestUtils.assertEqualsToFile(testDataFilePath.resolveSibling(testDataFilePath.nameWithoutExtension + ".mmd"), mermaid)
        }
    }

    fun collectModules(): List<KaModule> {
        val files = testDataFiles.map { getFile(it.path).getPsiFile(project) }
        val modules = files.map { it.getKaModule(project, useSiteModule = null) }
        return modules.computeDependenciesClosure()
    }
}