// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.projectStructure

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.psi.search.FilenameIndex
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.analysis.api.projectStructure.allDirectDependencies
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.base.fir.projectStructure.KaModuleStructureMermaidRenderer
import org.jetbrains.kotlin.idea.base.fir.projectStructure.KaModuleStructureTxtRenderer
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.params.ParameterizedTest
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.sequences.forEach

@TestRoot("gradle/scripting/kotlin.gradle.scripting.k2/")
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
            val allModules = runReadAction { collectModules() }
            runReadAction { validateScriptModules(allModules) }

            val (txt, mermaid) = runReadAction {
                val txt = KaModuleStructureTxtRenderer.render(allModules)
                val mermaid = KaModuleStructureMermaidRenderer.render(allModules)
                txt to mermaid
            }

            val testDataFilePath = dataFile().toPath()
            KotlinTestUtils.assertEqualsToFile(
                testDataFilePath.resolveSibling(testDataFilePath.nameWithoutExtension + ".txt").toFile(),
                txt
            ) { replaceLocalPathWithPlaceholder(it, project) }

            KotlinTestUtils.assertEqualsToFile(
                testDataFilePath.resolveSibling(testDataFilePath.nameWithoutExtension + ".mmd").toFile(),
                mermaid
            ) { replaceLocalPathWithPlaceholder(it, project) }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun validateScriptModules(modules: List<KaModule>) {
        val allVirtualFiles = getAllVirtualFiles()
        for (module in modules.filterIsInstance<KaScriptModule>()) {
            val scope = module.contentScope
            JUnit4Assertions.assertTrue(module.file.virtualFile in scope)

            val excludedFiles = allVirtualFiles - module.file.virtualFile
            val excludedFilesInScope = excludedFiles.filter { it in scope }
            JUnit4Assertions.assertTrue(excludedFilesInScope.isEmpty()) {
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


    @OptIn(KaPlatformInterface::class)
    private fun collectModules(): List<KaModule> {
        val files = testDataFiles.map { getFile(it.path).getPsiFile(project) }
        val modules = files.map { it.getKaModule(project, useSiteModule = null) }
        return modules.computeDependenciesClosure()
            // should not be here empty, mitigation of KT-74010
            .filter { it !is KaBuiltinsModule }
    }

    private fun Collection<KaModule>.computeDependenciesClosure(): List<KaModule> {
        val result = mutableSetOf<KaModule>()

        fun visit(module: KaModule) {
            if (module in result) return
            result += module
            module.allDirectDependencies().forEach(::visit)
        }

        forEach(::visit)

        return result.toList()
    }

    /**
     * jar:///home/neon/.m2/repository/org/jetbrains/annotations/13.0/annotations-13.0.jar!/
     * =>
     * jar:///MAVEN_REPOSITORY/org/jetbrains/annotations/13.0/annotations-13.0.jar!/
     */
    private fun replaceLocalPathWithPlaceholder(input: String, project: Project): String {
        val gradleLocalPath = getGradleLocalRepoPath(project).invariantSeparatorsPathString.removeSuffix("/")
        val javaHome = getJavaHomePath(project).invariantSeparatorsPathString.removeSuffix("/")
        val projectPath = getBaseProjectPath(project).invariantSeparatorsPathString.removeSuffix("/")
        val userHome = Path(System.getProperty("user.home")).invariantSeparatorsPathString.removeSuffix("/")
        return input
            .replace(gradleLocalPath, GRADLE_IML_PLACEHOLDER, ignoreCase = false)
            .replace(javaHome, JAVA_HOME_PLACEHOLDER, ignoreCase = false)
            .replace(projectPath, PROJECT_IML_PLACEHOLDER, ignoreCase = false)
            .replace(userHome, USER_HOME_PLACEHOLDER, ignoreCase = false)
    }

    private fun getGradleLocalRepoPath(project: Project): Path {
        return GradleInstallationManager.getInstance()
            .getGradleHomePath(project, project.stateStore.projectBasePath.invariantSeparatorsPathString)
            ?.subPaths()
            ?.first { it.last() == Path("gradle") || it.last() == Path(".gradle") } ?: Path(".gradle")
    }

    private fun Path.subPaths(): List<Path> =
        if (root != null) {
            runningFold(root) { acc, value -> acc.resolve(value) }.run { subList(1, size) }
        } else {
            runningReduce { acc, value -> acc.resolve(value) }
        }

    private val GRADLE_IML_PLACEHOLDER = "GRADLE_REPOSITORY"
    private val JAVA_HOME_PLACEHOLDER = "JAVA_HOME"
    private val USER_HOME_PLACEHOLDER = "USER_HOME"
    private val PROJECT_IML_PLACEHOLDER = "PROJECT_PLACEHOLDER"

    private fun getJavaHomePath(project: Project): Path = ProjectRootManager.getInstance(
        project
    ).projectSdk?.homeDirectory?.toNioPath()?.toAbsolutePath() ?: Path(System.getProperty("java.home")).toAbsolutePath()

    private fun getBaseProjectPath(project: Project): Path {
        return project.stateStore.projectBasePath
    }
}

