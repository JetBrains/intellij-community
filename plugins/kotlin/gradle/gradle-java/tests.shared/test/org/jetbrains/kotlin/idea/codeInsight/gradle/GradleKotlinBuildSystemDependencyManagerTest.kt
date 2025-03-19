// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleKotlinBuildSystemDependencyManager
import org.junit.Test
import kotlin.io.path.invariantSeparatorsPathString

class GradleKotlinBuildSystemDependencyManagerTest : KotlinGradleImportingTestCase() {
    private lateinit var gradleDependencyManager: GradleKotlinBuildSystemDependencyManager
    override fun testDataDirName(): String = "gradleKotlinBuildSystemDependencyManager"

    override fun setUp() {
        super.setUp()
        gradleDependencyManager = GradleKotlinBuildSystemDependencyManager(myProject)
    }

    @Test
    fun testGradleDependencyManagerExists() = runBlocking {
        importProjectFromTestData()
        val module = myProject.modules.first()
        val extensions = myProject.extensionArea.getExtensionPoint(KotlinBuildSystemDependencyManager.EP_NAME).extensionList
        val applicableConfigurators = extensions.filter { it.isApplicable(module) }
        assertSize(1, applicableConfigurators.filterIsInstance<GradleKotlinBuildSystemDependencyManager>())
    }

    private fun doGettingBuildFileTest(moduleName: String, expectedPath: String) {
        importProjectFromTestData()
        val module = myProject.modules.firstOrNull { it.name == moduleName }
        assertNotNull(module)
        val buildScript = runReadAction {
            gradleDependencyManager.getBuildScriptFile(module!!)
        }
        assertNotNull(buildScript)
        val buildScriptPath = buildScript!!.toNioPath()
        val projectPath = myProjectRoot.toNioPath()

        val relativePath = projectPath.relativize(buildScriptPath)
        assertEquals(expectedPath, relativePath.invariantSeparatorsPathString)
    }

    @Test
    fun testGettingBuildFile() = runBlocking {
        doGettingBuildFileTest("project.main", "build.gradle")
    }

    @Test
    fun testGettingBuildFileKts() = runBlocking {
        doGettingBuildFileTest("project.main", "build.gradle.kts")
    }

    @Test
    fun testGettingBuildFileSubmodule() = runBlocking {
        doGettingBuildFileTest("project.submodule.main", "submodule/build.gradle")
    }

    @Test
    fun testGettingBuildFileSubmoduleKts() = runBlocking {
        doGettingBuildFileTest("project.submodule.main", "submodule/build.gradle.kts")
    }

    private fun doDependencyTest(moduleName: String, scope: DependencyScope, submodules: List<String> = emptyList()) {
        val testLibraryDescriptor = ExternalLibraryDescriptor("org.test", "artifact", "1.2.3", "1.2.3", "1.2.3", scope)

        importProjectFromTestData()
        val module = myProject.modules.firstOrNull { it.name == moduleName }
        assertNotNull(module)
        WriteCommandAction.writeCommandAction(myProject).run<Throwable> {
            gradleDependencyManager.addDependency(module!!, testLibraryDescriptor)
        }
        val buildScriptFiles = runReadAction {
            myProject.modules.mapNotNull { gradleDependencyManager.getBuildScriptFile(it) }.distinct()
        }
        runInEdtAndWait {
            checkFilesInMultimoduleProject(buildScriptFiles, submodules)
        }
    }

    @Test
    fun testAddingDependency() = runBlocking {
        doDependencyTest("project.main", DependencyScope.COMPILE)
    }

    @Test
    fun testAddingDependencyKts() = runBlocking {
        doDependencyTest("project.main", DependencyScope.COMPILE)
    }

    @Test
    fun testAddingTestDependency() = runBlocking {
        doDependencyTest("project.test", DependencyScope.TEST)
    }

    @Test
    fun testAddingTestDependencyKts() = runBlocking {
        doDependencyTest("project.test", DependencyScope.TEST)
    }

    @Test
    fun testAddingDependencySubmodule() = runBlocking {
        doDependencyTest("project.submodule.main", DependencyScope.COMPILE, listOf("submodule"))
    }

    @Test
    fun testAddingDependencySubmoduleKts() = runBlocking {
        doDependencyTest("project.submodule.main", DependencyScope.COMPILE, listOf("submodule"))
    }
}