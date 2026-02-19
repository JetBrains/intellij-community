// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modifyModules
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.HeavyPlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties

class JpsKotlinBuildSystemDependencyManagerTest : HeavyPlatformTestCase() {
    private val jpsDependencyManager = JpsKotlinBuildSystemDependencyManager()

    fun testBuildFile() = runBlocking {
        assertNull(jpsDependencyManager.getBuildScriptFile(module))
    }

    fun testJpsDependencyManagerExists() = runBlocking {
        val extensions = project.extensionArea.getExtensionPoint(KotlinBuildSystemDependencyManager.EP_NAME).extensionList
        val applicableConfigurators = extensions.filter { it.isApplicable(module) }
        assertSize(1, applicableConfigurators.filterIsInstance<JpsKotlinBuildSystemDependencyManager>())
    }

    private fun getTestLibraryDescriptor(scope: DependencyScope) =
        ExternalLibraryDescriptor("org.test", "artifact", "1.2.3", "1.2.3", "1.2.3", scope)

    private fun addAndTestDependency(module: Module, libraryDependencyScope: DependencyScope) {
        val testLibraryDescriptor = getTestLibraryDescriptor(libraryDependencyScope)
        WriteCommandAction.writeCommandAction(project).run<Throwable> {
            jpsDependencyManager.addDependency(module, testLibraryDescriptor)
        }
        val hasLibrary = ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>().any {
            val library = it.library as? LibraryEx ?: return@any false
            val properties = library.properties as? RepositoryLibraryProperties ?: return@any false
            testLibraryDescriptor.preferredVersion == properties.version &&
                    testLibraryDescriptor.libraryGroupId == properties.groupId &&
                    testLibraryDescriptor.libraryArtifactId == properties.artifactId
        }
        assertTrue("Did not find added dependency in module library table", hasLibrary)
    }

    fun testAddDependency() = runBlocking {
        addAndTestDependency(module, DependencyScope.COMPILE)
    }

    fun testAddTestDependency() = runBlocking {
        addAndTestDependency(module, DependencyScope.TEST)
    }

    fun testReusingExistingDependency() = runBlocking {
        addAndTestDependency(module, DependencyScope.COMPILE)
        val module = project.modifyModules {
            newModule("submodule", JavaModuleType.getModuleType().id)
        }
        addAndTestDependency(module, DependencyScope.COMPILE)

        val allModuleLibraries = project.modules.flatMapTo(mutableSetOf()) { innerModule ->
            ModuleRootManager.getInstance(innerModule).orderEntries.filterIsInstance<LibraryOrderEntry>().mapNotNull { it.library }
        }
        assertEquals(1, allModuleLibraries.size)
    }

    fun testIfLibraryWithNameExists() {
        val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        runWriteActionAndWait {
            val library = projectLibraryTable.createLibrary("artifact") as LibraryEx
            val modifiableModule = ModuleRootManager.getInstance(module).modifiableModel
            modifiableModule.addLibraryEntry(library)
            modifiableModule.commit()
        }
        addAndTestDependency(module, DependencyScope.COMPILE)
        val renamedCorrectly =
            ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>().any { it.libraryName == "artifact1" }
        assertTrue("Could not find library with correctly renamed name when library with conflicting name exists", renamedCorrectly)
    }

    fun testAddDependencySubmodule() = runBlocking {
        val module = project.modifyModules {
            newModule("submodule", JavaModuleType.getModuleType().id)
        }
        addAndTestDependency(module, DependencyScope.COMPILE)
    }
}