// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import java.nio.file.Paths
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest

typealias ModulesByNames = Map<String, Module>

abstract class AbstractProjectStructureTest<S : TestProjectStructure> : AbstractMultiModuleTest() {
    protected fun initializeProjectStructure(path: String, parser: TestProjectStructureParser<S>): Pair<S, ModulesByNames> {
        val testStructure = TestProjectStructureReader.readToTestStructure(
            Paths.get(path),
            testProjectStructureParser = parser,
        )

        val modulesByNames = testStructure.modules.associate { moduleData ->
            moduleData.name to createEmptyModule(moduleData.name)
        }

        testStructure.modules.forEach { moduleData ->
            val module = modulesByNames.getValue(moduleData.name)
            moduleData.dependsOnModules.forEach { dependencyName ->
                module.addDependency(modulesByNames.getValue(dependencyName))
            }
        }

        return Pair(testStructure, modulesByNames)
    }

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
