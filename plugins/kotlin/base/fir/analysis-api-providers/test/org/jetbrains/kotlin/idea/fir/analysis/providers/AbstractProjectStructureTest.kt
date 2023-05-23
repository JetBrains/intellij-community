// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import java.nio.file.Paths
import org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure.TestProjectStructureReader
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.test.util.addDependency

typealias ProjectLibrariesByNames = Map<String, Library>
typealias ModulesByNames = Map<String, Module>

abstract class AbstractProjectStructureTest<S : TestProjectStructure> : AbstractMultiModuleTest() {
    protected fun initializeProjectStructure(
        path: String,
        parser: TestProjectStructureParser<S>,
    ): Triple<S, ProjectLibrariesByNames, ModulesByNames> {
        val testStructure = TestProjectStructureReader.readToTestStructure(
            Paths.get(path),
            testProjectStructureParser = parser,
        )

        val projectLibrariesByNames = testStructure.libraries.associate { libraryData ->
            libraryData.name to createEmptyProjectLibrary(libraryData.name)
        }

        val modulesByNames = testStructure.modules.associate { moduleData ->
            moduleData.name to createEmptyModule(moduleData.name)
        }

        val duplicateNames = projectLibrariesByNames.keys.intersect(modulesByNames.keys)
        if (duplicateNames.isNotEmpty()) {
            error("Test project libraries and modules may not share names. Duplicate names: ${duplicateNames.joinToString()}.")
        }

        testStructure.modules.forEach { moduleData ->
            val module = modulesByNames.getValue(moduleData.name)
            moduleData.dependsOnModules.forEach { dependencyName ->
                projectLibrariesByNames[dependencyName]
                    ?.let { library -> module.addDependency(library) }
                    ?: module.addDependency(modulesByNames.getValue(dependencyName))
            }
        }

        return Triple(testStructure, projectLibrariesByNames, modulesByNames)
    }

    private fun createEmptyProjectLibrary(name: String): Library = ConfigLibraryUtil.addProjectLibrary(project, name) { }

    private fun createEmptyModule(name: String): Module {
        val tmpDir = createTempDirectory().toPath()
        val module: Module = createModule("$tmpDir/$name", moduleType)
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tmpDir.toFile())!!
        WriteCommandAction.writeCommandAction(module.project).run<RuntimeException> {
            root.refresh(false, true)
        }

        PsiTestUtil.addSourceContentToRoots(module, root)
        return module
    }
}
