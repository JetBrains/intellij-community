// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirModuleSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.fir.analysis.providers.AbstractProjectStructureTest
import org.jetbrains.kotlin.idea.util.publishModuleOutOfBlockModification
import java.io.File

abstract class AbstractSessionsInvalidationTest : AbstractProjectStructureTest<SessionInvalidationTestProjectStructure>() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-providers").resolve("testData").resolve("sessionInvalidation")

    protected fun doTest(path: String) {
        val (testStructure, projectLibrariesByName, modulesByNames) =
            initializeProjectStructure(path, SessionInvalidationTestProjectStructureParser)

        val rootIdeaModule = modulesByNames.getValue(testStructure.rootModule)
        val rootModule = rootIdeaModule.getMainKtSourceModule()!!

        val sessionsBeforeOOBM = getAllModuleSessions(rootModule)

        val modulesToMakeOOBM = testStructure.modulesToMakeOOBM.map(modulesByNames::getValue)
        runWriteAction {
            modulesToMakeOOBM.forEach { module ->
                module.publishModuleOutOfBlockModification()
            }
        }

        val sessionsAfterOOBM = getAllModuleSessions(rootModule)

        checkInvalidatedModules(testStructure, sessionsBeforeOOBM, sessionsAfterOOBM)
        checkSessionsMarkedInvalid(sessionsBeforeOOBM, sessionsAfterOOBM)
    }

    private fun checkInvalidatedModules(
        testStructure: SessionInvalidationTestProjectStructure,
        sessionsBeforeOOBM: List<LLFirSession>,
        sessionsAfterOOBM: List<LLFirSession>,
    ) {
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

    private fun checkSessionsMarkedInvalid(
        sessionsBeforeOOBM: List<LLFirSession>,
        sessionsAfterOOBM: List<LLFirSession>,
    ) {
        val invalidSessions = buildSet {
            addAll(sessionsBeforeOOBM)
            removeAll(sessionsAfterOOBM)
        }

        invalidSessions.forEach { session ->
            assert(!session.isValid) { "The invalidated session `${session}` should have been marked invalid." }
        }
    }

    private fun getAllModuleSessions(mainModule: KtModule): List<LLFirSession> {
        val projectModules = ModuleManager.getInstance(project).modules
            .flatMap { it.sourceModuleInfos }
            .map { it.toKtModule() }

        val resolveSession = LLFirResolveSessionService.getInstance(project).getFirResolveSession(mainModule)
        return projectModules.map(resolveSession::getSessionFor)
    }
}
