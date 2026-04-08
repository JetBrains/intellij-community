// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.codeInspection.options.OptionControllerProvider
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.idea.test.UseK1PluginMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

@UseK1PluginMode
@TestApplication
class JpsKotlinBuildSystemDependencyManagerTest {

    companion object {
        // the project is reused
        val projectFixture: TestFixture<Project> = projectFixture()
    }

    // the module is recreated after each test to clean up module libraries
    val moduleFixture: TestFixture<Module> = projectFixture.moduleFixture("main")

    val sourceRootFixture: TestFixture<PsiDirectory> = moduleFixture.sourceRootFixture()

    val contextFileFixture: TestFixture<PsiFile> = sourceRootFixture.psiFileFixture("context.kt", "fun foo() {}")

    val submoduleFixture: TestFixture<Module> = projectFixture.moduleFixture("submodule", JavaModuleType.getModuleType().id)

    val project: Project
        get() = projectFixture.get()

    val module: Module
        get() = moduleFixture.get()

    val contextFile: PsiFile
        get() = contextFileFixture.get()

    val jpsDependencyManager =
        KotlinBuildSystemDependencyManager.findConfigurator<JpsKotlinBuildSystemDependencyManager>(project)

    @AfterEach
    fun tearDown() {
        with(LibraryTablesRegistrar.getInstance().getLibraryTable(project)) {
            runWriteActionAndWait {
                libraries.forEach(::removeLibrary)
            }
        }
    }

    @Test
    fun testBuildFile(): Unit = runBlocking {
        assertNull(jpsDependencyManager.getBuildScriptFile(module))
    }

    @Test
    fun testJpsDependencyManagerExists(): Unit = runBlocking {
        val extensions = project.extensionArea.getExtensionPoint(KotlinBuildSystemDependencyManager.EP_NAME).extensionList
        val applicableConfigurators = extensions.filter { it.isApplicable(module) }
        assertEquals(1, applicableConfigurators.filterIsInstance<JpsKotlinBuildSystemDependencyManager>().size)
    }

    private fun getTestLibraryDescriptor(scope: DependencyScope) =
        ExternalLibraryDescriptor("org.test", "artifact", "1.2.3", "1.2.3", "1.2.3", scope)

    @Test
    fun testAddDependency(): Unit = runBlocking {
        addAndTestDependency(module, DependencyScope.COMPILE)
    }

    @Test
    fun testAddTestDependency(): Unit = runBlocking {
        addAndTestDependency(module, DependencyScope.TEST)
    }

    @Test
    fun testReusingExistingDependency() {
        runBlocking {
            addAndTestDependency(module, DependencyScope.COMPILE)
        }

        runBlocking {
            addAndTestDependency(submoduleFixture.get(), DependencyScope.COMPILE)
        }

        val allModuleLibraries = project.modules.flatMapTo(mutableSetOf()) { innerModule ->
            ModuleRootManager.getInstance(innerModule).orderEntries.filterIsInstance<LibraryOrderEntry>().mapNotNull { it.library }
        }
        assertEquals(1, allModuleLibraries.size)
    }

    @Test
    fun testIfLibraryWithNameExists() = runBlocking {
        val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        runWriteActionAndWait {
            val library = projectLibraryTable.createLibrary("artifact") as LibraryEx
            val modifiableModule = ModuleRootManager.getInstance(module).modifiableModel
            modifiableModule.addLibraryEntry(library)
            modifiableModule.commit()
            library
        }

        addAndTestDependency(module, DependencyScope.COMPILE)
        val renamedCorrectly =
            runReadActionBlocking {
                ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>()
                    .any { it.libraryName == "artifact1" }
            }
        assertTrue(renamedCorrectly, "Could not find library with correctly renamed name when library with conflicting name exists")
    }

    @Test
    fun testAddDependencySubmodule(): Unit = runBlocking {
        addAndTestDependency(submoduleFixture.get(), DependencyScope.COMPILE)
    }

    private suspend fun addAndTestDependency(module: Module, libraryDependencyScope: DependencyScope) {
        val testLibraryDescriptor = getTestLibraryDescriptor(libraryDependencyScope)

        val dependencyProvider =
            (OptionControllerProvider.EP_NAME.extensionList.firstOrNull { it.name() == JpsDependencyProvider.NAME } as? JpsDependencyProvider
                ?: error("JpsDependencyProvider is not found"))
        val jobReference = AtomicReference<Job>()
        dependencyProvider.jobReference = jobReference

        val actionContext = ActionContext.from(null, contextFile)

        val modCommand = jpsDependencyManager.addDependencyModCommand(contextFile, module, testLibraryDescriptor)

        writeCommandAction(project, "") {
            ModCommandExecutor.getInstance().executeInteractively(actionContext, modCommand, null)
        }

        jobReference.getAndSet(null)?.join()

        launchOnEdtAndDispatchAllEvents()

        val hasLibrary = runReadActionBlocking {
            val libraryOrderEntries = ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>()
            libraryOrderEntries.any {
                val library = it.library as? LibraryEx ?: return@any false
                val properties = library.properties as? RepositoryLibraryProperties ?: return@any false
                testLibraryDescriptor.preferredVersion == properties.version && testLibraryDescriptor.libraryGroupId == properties.groupId && testLibraryDescriptor.libraryArtifactId == properties.artifactId
            }
        }
        assertTrue(hasLibrary, "Did not find added dependency in module library table")
    }

    private suspend fun launchOnEdtAndDispatchAllEvents() {
        withContext(Dispatchers.EDT) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }

}