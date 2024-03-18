// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.projectStructureTest.AbstractProjectStructureTest
import org.jetbrains.kotlin.idea.test.projectStructureTest.ModulesByName
import org.jetbrains.kotlin.idea.test.projectStructureTest.ProjectLibrariesByName
import java.io.File

sealed class AbstractSessionInvalidationTest : AbstractProjectStructureTest<SessionInvalidationTestProjectStructure>() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-providers").resolve("testData").resolve("sessionInvalidation")

    protected abstract fun publishModificationEvents(
        testStructure: SessionInvalidationTestProjectStructure,
        projectLibrariesByName: ProjectLibrariesByName,
        modulesByName: ModulesByName,
    )

    protected abstract fun checkSessions(
        testStructure: SessionInvalidationTestProjectStructure,
        sessionsBeforeModification: List<LLFirSession>,
        sessionsAfterModification: List<LLFirSession>,
    )

    protected fun checkSessionsMarkedInvalid(
        sessionsBeforeModification: List<LLFirSession>,
        sessionsAfterModification: List<LLFirSession>,
    ) {
        val invalidSessions = buildSet {
            addAll(sessionsBeforeModification)
            removeAll(sessionsAfterModification)
        }

        invalidSessions.forEach { session ->
            assertFalse("The invalidated session `${session}` should have been marked invalid.", session.isValid)
        }
    }

    protected fun doTest(path: String) {
        val (testStructure, projectLibrariesByName, modulesByName) =
            initializeProjectStructure(path, SessionInvalidationTestProjectStructureParser)

        val rootIdeaModule = modulesByName.getValue(testStructure.rootModule)
        val rootModule = rootIdeaModule.getMainKtSourceModule()!!

        val sessionsBeforeModification = getAllModuleSessions(rootModule)

        publishModificationEvents(testStructure, projectLibrariesByName, modulesByName)

        val sessionsAfterModification = getAllModuleSessions(rootModule)

        checkSessions(testStructure, sessionsBeforeModification, sessionsAfterModification)
    }

    private fun getAllModuleSessions(mainModule: KtModule): List<LLFirSession> {
        val projectModules = ModuleManager.getInstance(mainModule.project).modules
            .flatMap { it.sourceModuleInfos }
            .map { it.toKtModule() }

        val resolveSession = LLFirResolveSessionService.getInstance(mainModule.project).getFirResolveSession(mainModule)
        return projectModules.map(resolveSession::getSessionFor)
    }
}

