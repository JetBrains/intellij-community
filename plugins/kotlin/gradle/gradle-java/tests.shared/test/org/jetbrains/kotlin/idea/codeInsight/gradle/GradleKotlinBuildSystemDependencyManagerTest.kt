// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleKotlinBuildSystemDependencyManager
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.invariantSeparatorsPathString

class GradleKotlinBuildSystemDependencyManagerTest : KotlinGradleImportingTestCase() {
    private lateinit var gradleDependencyManager: GradleKotlinBuildSystemDependencyManager
    override fun testDataDirName(): String = "gradleKotlinBuildSystemDependencyManager"

    override fun setUp() {
        super.setUp()
        gradleDependencyManager = KotlinBuildSystemDependencyManager.findConfigurator<GradleKotlinBuildSystemDependencyManager>(myProject)
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

    private suspend fun doDependencyTest(moduleName: String, scope: DependencyScope, submodules: List<String> = emptyList()) {
        val testLibraryDescriptor = ExternalLibraryDescriptor("org.test", "artifact", "1.2.3", "1.2.3", "1.2.3", scope)

        importProjectFromTestData()
        val module = myProject.modules.firstOrNull { it.name == moduleName } ?: error("No module '$moduleName' found")

        val dependencyProvider = KotlinDependencyProvider.getInstance()
        val jobReference = AtomicReference<Job>()
        dependencyProvider.jobReference = jobReference

        val buildScript = readAction { gradleDependencyManager.getBuildScriptFile(module) }
            ?: error("No pom.xml for module $moduleName")

        val contextFile = readAction { PsiManager.getInstance(myProject).findFile(buildScript) }
            ?: error("No pom.xml for module $moduleName")

        val actionContext = ActionContext.from(null, contextFile)

        val modCommand =
            readAction {
                gradleDependencyManager.addDependencyModCommand(contextFile, module, testLibraryDescriptor)
            }

        writeCommandAction(myProject, "") {
            ModCommandExecutor.getInstance().executeInteractively(actionContext, modCommand, null)
        }

        jobReference.getAndSet(null)!!.join()

        withContext(Dispatchers.EDT) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        val buildScriptFiles = readAction {
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