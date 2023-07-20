// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirModuleSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectModule
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructure
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructureReader
import org.jetbrains.kotlin.idea.fir.analysis.providers.incModificationTracker
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import java.io.File
import java.nio.file.Paths

abstract class AbstractSessionsInvalidationTest : AbstractMultiModuleTest() {
    override fun isFirPlugin(): Boolean = true

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

        val rootIdeaModule = modulesByNames.getValue(testStructure.rootModule)
        val rootModule = rootIdeaModule.getMainKtSourceModule()!!

        val sessionsBeforeOOBM = getAllModuleSessions(rootModule)

        val modulesToMakeOOBM = testStructure.modulesToMakeOOBM.map(modulesByNames::getValue)
        modulesToMakeOOBM.forEach { it.incModificationTracker() }

        val sessionsAfterOOBM = getAllModuleSessions(rootModule)

        val changedSessions = buildSet {
            addAll(sessionsBeforeOOBM)
            addAll(sessionsAfterOOBM)
            removeAll(sessionsBeforeOOBM.intersect(sessionsAfterOOBM.toSet()))
        }

        val changedSessionsModuleNames = changedSessions
            .map { session ->
                val moduleSession = session as LLFirModuleSession
                val module = moduleSession.ktModule as KtSourceModule
                module.moduleName
            }
            .distinct()
            .sorted()

        assertEquals(testStructure.expectedInvalidatedModules, changedSessionsModuleNames)
    }

    private fun getAllModuleSessions(mainModule: KtModule): List<LLFirSession> {
        val projectModules = ModuleManager.getInstance(project).modules
            .flatMap { it.sourceModuleInfos }
            .map { it.toKtModule() }

        val resolveSession = LLFirResolveSessionService.getInstance(project).getFirResolveSession(mainModule)
        return projectModules.map(resolveSession::getSessionFor)
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