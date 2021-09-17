// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import junit.framework.Assert
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.FirIdeModuleSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.FirIdeSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.FirIdeSessionProviderStorage
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.fir.analysis.project.structure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectModule
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructure
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructureReader
import org.jetbrains.kotlin.idea.fir.analysis.providers.incModificationTracker
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.test.KotlinRoot
import java.io.File
import java.nio.file.Paths

abstract class AbstractSessionsInvalidationTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("fir-low-level-api-ide-impl").resolve("testData").resolve("sessionInvalidation")

    protected fun doTest(path: String) {
        val testStructure = TestProjectStructureReader.readToTestStructure(
            Paths.get(path),
            toTestStructure = MultiModuleTestProjectStructure.Companion::fromTestProjectStructure
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

        val rootModule = modulesByNames[testStructure.rootModule]
            ?: error("${testStructure.rootModule} is not present in the list of modules")
        val modulesToMakeOOBM = testStructure.modulesToMakeOOBM.map {
            modulesByNames[it]
                ?: error("$it is not present in the list of modules")
        }

        val rootModuleSourceInfo = rootModule.getMainKtSourceModule()!!

        val storage = FirIdeSessionProviderStorage(project)

        val initialSessions = storage.getFirSessions(rootModuleSourceInfo)
        modulesToMakeOOBM.forEach { it.incModificationTracker() }
        val sessionsAfterOOBM = storage.getFirSessions(rootModuleSourceInfo)

        val intersection = com.intellij.util.containers.ContainerUtil.intersection(initialSessions, sessionsAfterOOBM)
        val changedSessions = HashSet(initialSessions)
        changedSessions.addAll(sessionsAfterOOBM)
        changedSessions.removeAll(intersection)
        val changedSessionsModulesNamesSorted = changedSessions
            .map { session ->
                val moduleSession = session as FirIdeModuleSession
                val module = moduleSession.module as KtSourceModule
                module.moduleName
            }
            .distinct()
            .sorted()

        Assert.assertEquals(testStructure.expectedInvalidatedModules, changedSessionsModulesNamesSorted)
    }

    private fun FirIdeSessionProviderStorage.getFirSessions(module: KtSourceModule): Set<FirIdeSession> {
        val sessionProvider = getSessionProvider(module)
        return sessionProvider.sessions.values.toSet()
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

private data class MultiModuleTestProjectStructure(
    val modules: List<TestProjectModule>,
    val rootModule: String,
    val modulesToMakeOOBM: List<String>,
    val expectedInvalidatedModules: List<String>,
) {
    companion object {
        fun fromTestProjectStructure(testProjectStructure: TestProjectStructure): MultiModuleTestProjectStructure {
            val json = testProjectStructure.json

            return MultiModuleTestProjectStructure(
                testProjectStructure.modules,
                json.getString(ROOT_MODULE_FIELD),
                json.getAsJsonArray(MODULES_TO_MAKE_OOBM_IN_FIELD).map { it.asString }.sorted(),
                json.getAsJsonArray(EXPECTED_INVALIDATED_MODULES_FIELD).map { it.asString }.sorted(),
            )
        }

        private const val ROOT_MODULE_FIELD = "rootModule"
        private const val MODULES_TO_MAKE_OOBM_IN_FIELD = "modulesToMakeOOBM"
        private const val EXPECTED_INVALIDATED_MODULES_FIELD = "expectedInvalidatedModules"
    }
}